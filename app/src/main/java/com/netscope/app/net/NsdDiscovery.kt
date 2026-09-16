package com.netscope.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

/**
 * mDNS / DNS-SD (Bonjour) discovery of advertised services on the LAN.
 * This is fully passive: devices broadcast these records to announce themselves.
 */
class NsdDiscovery(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()

    private val serviceTypes = listOf(
        "_http._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_raop._tcp.",
        "_spotify-connect._tcp.", "_printer._tcp.", "_ipp._tcp.", "_ipps._tcp.",
        "_hap._tcp.", "_sonos._tcp.", "_workstation._tcp.", "_smb._tcp.",
        "_androidtvremote2._tcp.", "_amzn-wplay._tcp.",
    )

    /** friendly label describing what a service type is */
    private fun label(type: String): String = when {
        type.contains("googlecast") -> "Chromecast"
        type.contains("airplay") || type.contains("raop") -> "AirPlay"
        type.contains("spotify") -> "Spotify Connect"
        type.contains("printer") || type.contains("ipp") -> "Printer"
        type.contains("hap") -> "HomeKit"
        type.contains("sonos") -> "Sonos"
        type.contains("smb") || type.contains("workstation") -> "File share"
        type.contains("androidtv") || type.contains("amzn") -> "TV/Streamer"
        type.contains("http") -> "Web service"
        else -> type.trim('.').removePrefix("_")
    }

    fun start(onService: (name: String, kind: String, host: String?, port: Int) -> Unit) {
        serviceTypes.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

                override fun onServiceFound(info: NsdServiceInfo) {
                    resolve(info, type, onService)
                }

                override fun onServiceLost(info: NsdServiceInfo) {}
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(
        info: NsdServiceInfo,
        type: String,
        onService: (String, String, String?, Int) -> Unit,
    ) {
        val cb = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onService(info.serviceName ?: "?", label(type), null, 0)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    serviceInfo.host?.hostAddress
                }
                onService(serviceInfo.serviceName ?: "?", label(type), host, serviceInfo.port)
            }
        }
        try {
            nsd.resolveService(info, cb)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        listeners.forEach {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        listeners.clear()
    }
}
