package com.netscope.app.util

/**
 * Tiny built-in OUI (MAC vendor prefix) lookup.
 *
 * A full IEEE OUI registry is ~35k entries; bundling it is out of scope for this
 * sample. This covers common consumer vendors so the vendor column is useful.
 * Replace [table] with an assets-loaded copy of the IEEE "oui.csv" for full coverage.
 */
object Oui {
    private val table: Map<String, String> = mapOf(
        "FCFBFB" to "Apple", "F0F61C" to "Apple", "A4B197" to "Apple",
        "3C0754" to "Apple", "D0817A" to "Apple", "88665A" to "Apple",
        "DCA904" to "Apple", "F80377" to "Apple",
        "E4E0C5" to "Samsung", "5CE8EB" to "Samsung", "8425DB" to "Samsung",
        "78BDBC" to "Samsung", "34145F" to "Samsung",
        "B827EB" to "Raspberry Pi", "DCA632" to "Raspberry Pi", "E45F01" to "Raspberry Pi",
        "18B430" to "Nest/Google", "F4F5D8" to "Google", "A47733" to "Google",
        "3C5AB4" to "Google", "1CF29A" to "Google",
        "00D861" to "Micro-Star", "D8BBC1" to "Micro-Star",
        "001788" to "Philips Hue", "ECB5FA" to "Philips Hue",
        "44650D" to "Amazon", "FCA667" to "Amazon", "68544C" to "Amazon", "50DCE7" to "Amazon",
        "B0A737" to "Roku", "DC3A5E" to "Roku",
        "D052A8" to "Sonos", "5CAAFD" to "Sonos", "347E5C" to "Sonos",
        "000C43" to "Ralink/TP-Link", "50C7BF" to "TP-Link", "C46E1F" to "TP-Link",
        "AC84C6" to "TP-Link", "1C61B4" to "TP-Link",
        "F81A67" to "TP-Link", "E894F6" to "TP-Link",
        "C0562D" to "Xiaomi", "F0B429" to "Xiaomi", "286C07" to "Xiaomi", "64B473" to "Xiaomi",
        "5C879C" to "Bose", "08DF1F" to "Bose",
        "00025B" to "Cambridge/JBL", "0021BA" to "Harman",
        "001A11" to "Google", "9C8ECD" to "Sony", "FC0FE7" to "Sony", "104FA8" to "Sony",
    )

    fun lookup(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        val prefix = mac.replace(":", "").replace("-", "").uppercase().take(6)
        return table[prefix]
    }
}
