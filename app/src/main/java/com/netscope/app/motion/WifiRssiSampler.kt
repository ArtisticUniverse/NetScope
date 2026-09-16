package com.netscope.app.motion

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Reads the RSSI of the currently-associated Wi-Fi access point. Unlike
 * [WifiManager.startScan] (heavily throttled), the associated-AP RSSI can be
 * polled continuously, which is what both the heatmap and motion features need.
 */
class WifiRssiSampler(context: Context) {

    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    data class Sample(val rssi: Int, val connected: Boolean, val ssid: String?)

    @Suppress("DEPRECATION")
    fun read(): Sample {
        return try {
            val info = wifi?.connectionInfo
            val rssi = info?.rssi ?: -127
            // Do NOT rely on networkId: on API 31+ it is redacted to -1 without
            // location permission even while associated. A plausible RSSI is the
            // robust "connected" signal (unassociated returns -127 / 0).
            val connected = rssi in -100..-1
            val ssid = info?.ssid?.takeIf { it != "<unknown ssid>" }?.trim('"')
            Sample(rssi, connected, ssid)
        } catch (_: Exception) {
            Sample(-127, false, null)
        }
    }
}
