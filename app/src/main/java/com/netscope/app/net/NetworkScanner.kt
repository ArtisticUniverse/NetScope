package com.netscope.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiManager
import com.netscope.app.model.NetDevice
import com.netscope.app.model.PortInfo
import com.netscope.app.util.Oui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Passive/active local-network discovery, limited to what any device on the same
 * LAN can legitimately observe: liveness (ICMP/TCP), the ARP table, reverse DNS,
 * and open well-known ports. It does NOT capture traffic, exploit, or access any
 * device's private data — that is the "ethical visibility" boundary.
 */
class NetworkScanner(context: Context) {

    private val appContext = context.applicationContext

    /** Common ports we probe, mapped to their well-known service name. */
    private val commonPorts = linkedMapOf(
        22 to "SSH", 23 to "Telnet", 53 to "DNS", 80 to "HTTP",
        139 to "SMB", 443 to "HTTPS", 445 to "SMB", 515 to "Printer",
        554 to "RTSP", 631 to "IPP/Print", 1883 to "MQTT", 3389 to "RDP",
        5000 to "UPnP/HTTP", 5353 to "mDNS", 7000 to "AirPlay",
        8008 to "Chromecast", 8009 to "Chromecast", 8080 to "HTTP-alt",
        8443 to "HTTPS-alt", 8883 to "MQTT-TLS", 9100 to "RawPrint", 62078 to "iOS-sync",
    )

    data class LocalInfo(
        val localIp: String,
        val gatewayIp: String?,
        val prefixLength: Int,
        val ssid: String?,
    )

    fun localInfo(): LocalInfo? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return null
        val lp: LinkProperties = cm.getLinkProperties(active) ?: return null
        val v4 = lp.linkAddresses.firstOrNull { it.address.address.size == 4 } ?: return null
        val ip = v4.address.hostAddress ?: return null

        val wifi = appContext.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val gateway = wifi?.dhcpInfo?.gateway?.let { intToIp(it) }
        @Suppress("DEPRECATION")
        val ssid = wifi?.connectionInfo?.ssid?.takeIf { it != "<unknown ssid>" }

        return LocalInfo(ip, gateway, v4.prefixLength, ssid)
    }

    /**
     * Scan the local subnet. Emits each discovered device via [onDevice] as it is
     * found, and progress (0f..1f) via [onProgress].
     */
    suspend fun scan(
        onProgress: (Float) -> Unit,
        onDevice: (NetDevice) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val info = localInfo() ?: return@withContext
        val base = info.localIp.substringBeforeLast('.')  // treat as /24 sweep
        val hosts = (1..254).map { "$base.$it" }
        val total = hosts.size
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val arp = readArpTable()
        val gate = 32

        coroutineScope {
            val sem = Semaphore(gate)
            hosts.map { host ->
                async {
                    sem.withPermit {
                        // Fast liveness first; only fully port-scan hosts that are up.
                        val alive = isReachable(host) || anyOpen(host, livenessPorts)
                        if (alive) {
                            val ports = quickPortScan(host)
                            val mac = arp[host]
                            val device = NetDevice(
                                ip = host,
                                mac = mac,
                                hostname = reverseDns(host),
                                vendor = Oui.lookup(mac),
                                isGateway = host == info.gatewayIp,
                                isSelf = host == info.localIp,
                                openPorts = ports,
                                services = ports.map { it.service }.distinct(),
                            )
                            onDevice(device)
                        }
                        onProgress(done.incrementAndGet().toFloat() / total)
                    }
                }
            }.awaitAll()
        }
    }

    /** Small set of ports used only to decide if a host is alive. */
    private val livenessPorts = listOf(80, 443, 22, 445, 8080)

    private fun isReachable(host: String): Boolean =
        try {
            InetAddress.getByName(host).isReachable(500)
        } catch (_: Exception) {
            false
        }

    private fun anyOpen(host: String, ports: List<Int>): Boolean =
        ports.any { tcpOpen(host, it, 200) }

    private fun quickPortScan(host: String): List<PortInfo> {
        val open = ArrayList<PortInfo>()
        for ((port, name) in commonPorts) {
            if (tcpOpen(host, port, 220)) open.add(PortInfo(port, name))
        }
        return open
    }

    private fun tcpOpen(host: String, port: Int, timeoutMs: Int): Boolean =
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }

    private fun reverseDns(host: String): String? =
        try {
            val name = InetAddress.getByName(host).canonicalHostName
            if (name == host) null else name
        } catch (_: Exception) {
            null
        }

    /** Best-effort ARP read. Often restricted on modern Android; failures are silent. */
    private fun readArpTable(): Map<String, String> {
        val result = HashMap<String, String>()
        val f = File("/proc/net/arp")
        if (!f.canRead()) return result
        try {
            BufferedReader(FileReader(f)).useLines { lines ->
                lines.drop(1).forEach { line ->
                    val cols = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (cols.size >= 4) {
                        val ip = cols[0]
                        val mac = cols[3]
                        if (mac != "00:00:00:00:00:00") result[ip] = mac.uppercase()
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun intToIp(addr: Int): String =
        "${addr and 0xFF}.${addr shr 8 and 0xFF}.${addr shr 16 and 0xFF}.${addr shr 24 and 0xFF}"
}
