package com.droid.dolphy.ble.smarthome

import android.content.Context
import com.droid.dolphy.DolphyApplication
import java.util.Locale

object BleVendorDb {
    @Volatile
    private var cached: Map<String, String>? = null

<<<<<<< HEAD
    fun getVendorLabel(context: Context? = null, macAddress: String?): String {
        if (macAddress.isNullOrBlank()) return "Unknown"
        val clean = macAddress.uppercase(Locale.ROOT)
            .replace("-", ":")
            .trim()
        val parts = clean.split(":")
        val oui = if (parts.size >= 3) {
            "${parts[0]}:${parts[1]}:${parts[2]}"
        } else if (clean.length >= 6) {
            clean.take(6).chunked(2).joinToString(":")
        } else {
            return "Unknown"
        }

        val ctx = context ?: runCatching { DolphyApplication.instance }.getOrNull()
        val db = cached ?: (ctx?.let { load(it) } ?: emptyMap()).also { if (it.isNotEmpty()) cached = it }
        return db[oui] ?: "Unknown"
=======
    fun getVendorLabel(context: Context, macAddress: String?): String {
        val mac = macAddress?.uppercase(Locale.ROOT) ?: return "Unknown"
        val db = cached ?: load(context).also { cached = it }
        return VendorPrefixes.lookup(db, mac) ?: "Unknown"
>>>>>>> upstream/main
    }

    fun getCompanyIdentifierName(companyId: Int): String? = when (companyId) {
        0x0000 -> "Ericsson Technology"
        0x0001 -> "Nokia Mobile Phones"
        0x0002 -> "Intel Corp."
        0x0003 -> "IBM Corp."
        0x0004 -> "Toshiba Corp."
        0x0006 -> "Microsoft"
        0x0007 -> "Lucent"
        0x0008 -> "Motorola"
        0x0009 -> "Infineon Technologies"
        0x000A -> "Qualcomm Technologies"
        0x000B -> "Silicon Wave"
        0x000D -> "Texas Instruments"
        0x000F -> "Broadcom Corporation"
        0x001D -> "Qualcomm (CSR)"
        0x002B -> "Polar Electro"
        0x002D -> "Sony Mobile"
        0x0030 -> "STMicroelectronics"
        0x004C -> "Apple, Inc."
        0x0056 -> "Sony"
        0x0057 -> "Bang & Olufsen"
        0x0059 -> "Sony Corporation"
        0x005D -> "Realtek Semiconductor"
        0x005F -> "Nordic Semiconductor"
        0x0067 -> "GN Audio / Jabra"
        0x006B -> "Plantronics / Poly"
        0x0075 -> "Samsung Electronics"
        0x007D -> "HUAWEI Technologies"
        0x0087 -> "Garmin International"
        0x00A0 -> "Sony Ericsson"
        0x00D2 -> "LG Electronics"
        0x00E0 -> "Google LLC"
        0x0105 -> "Harman / JBL"
        0x0131 -> "Bose Corporation"
        0x0147 -> "Philips Consumer Lifestyle"
        0x0157 -> "Xiaomi Communications"
        0x0163 -> "Tesla Motors"
        0x0171 -> "Amazon.com"
        0x01AB -> "Anker Innovations"
        0x01DA -> "Logitech Inc."
        0x0211 -> "Fitbit, Inc."
        0x0220 -> "Beats Electronics"
        0x025A -> "Amazon Technologies"
        0x027D -> "Sennheiser"
        0x02B0 -> "Nintendo Co., Ltd."
        0x02D3 -> "Tile, Inc."
        0x02DF -> "Valve Corporation"
        0x02E5 -> "Espressif Systems"
        0x038F -> "Xiaomi Inc."
        0x0399 -> "OnePlus Electronics"
        0x039E -> "OPPO"
        0x03B4 -> "Marshall / Zound"
        0x0440 -> "Skullcandy"
        0x0499 -> "Razer Inc."
        0x04B7 -> "Tuya Smart"
        0x04F2 -> "GoPro"
        0x05A7 -> "Sonos, Inc."
        0x05F7 -> "DJI"
        0x078E -> "TP-Link"
        0x0837 -> "Vivo Mobile"
        0x0876 -> "Sony Interactive Entertainment (PlayStation)"
        0x0948 -> "Motorola Mobility"
        0x0986 -> "Nothing Technology"
        0x098F -> "Nothing Technology"
        0x0A06 -> "Honor Device Co."
        0x0A2D -> "Realme Chongqing Mobile"
        0x0A82 -> "Huami / Amazfit"
        0x0AC7 -> "Zepp Health"
        0x424E -> "Ninebot / Segway"
        0x4E42 -> "Ninebot"
        0xFE2C -> "Google Fast Pair"
        0xFE95 -> "Xiaomi Inc."
        0xFEE0, 0xFEE1 -> "Amazfit / Xiaomi"
        else -> null
    }

    private fun load(context: Context): Map<String, String> {
        return runCatching {
            val map = HashMap<String, String>(40_000)
            context.assets.open("ble/nmap-mac-prefixes.txt").bufferedReader().useLines { lines ->
<<<<<<< HEAD
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach

                    val firstWs = line.indexOfFirst { it.isWhitespace() }
                    if (firstWs == -1) return@forEach
                    val hex = line.substring(0, firstWs).trim()
                    val vendor = line.substring(firstWs).trim()
                    if (hex.length >= 6) {
                        val oui = hex.take(6).chunked(2).joinToString(":") { it.uppercase(Locale.ROOT) }
                        map[oui] = vendor
                    }
=======
                lines.forEach { line ->
                    VendorPrefixes.parseLine(line)?.let { (oui, vendor) -> map[oui] = vendor }
>>>>>>> upstream/main
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}
