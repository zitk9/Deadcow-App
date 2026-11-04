package com.droid.dolphy.ble.smarthome

import android.content.Context
import java.util.Locale

object BleVendorDb {
    @Volatile
    private var cached: Map<String, String>? = null

    fun getVendorLabel(context: Context, macAddress: String?): String {
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

        val db = cached ?: load(context).also { cached = it }
        return db[oui] ?: "Unknown"
    }

    fun getCompanyIdentifierName(companyId: Int): String? = when (companyId) {
        0x004C -> "Apple, Inc."
        0x0075 -> "Samsung Electronics"
        0x0006 -> "Microsoft"
        0x00E0 -> "Google LLC"
        0x0157 -> "Xiaomi Communications"
        0x038F -> "Xiaomi Inc."
        0x0837 -> "Vivo Mobile"
        0x0059 -> "Sony Corporation"
        0x0105 -> "Harman / JBL"
        0x0131 -> "Bose Corporation"
        0x0220 -> "Beats Electronics"
        0x0087 -> "Garmin International"
        0x000A -> "Qualcomm Technologies"
        0x0002 -> "Intel Corp."
        0x005D -> "Realtek Semiconductor"
        0x005F -> "Nordic Semiconductor"
        0x02E5 -> "Espressif Systems"
        0x0030 -> "STMicroelectronics"
        0x0001 -> "Nokia Mobile Phones"
        0x000F -> "Broadcom Corporation"
        0x007D -> "HUAWEI Technologies"
        0x01DA -> "Logitech Inc."
        0x00D2 -> "LG Electronics"
        0x01AB -> "Anker Innovations"
        0x0399 -> "OnePlus Electronics"
        0x00A0 -> "Sony Ericsson"
        0x002D -> "Sony Mobile"
        0x025A -> "Amazon Technologies"
        0x0163 -> "Tesla Motors"
        0x02D3 -> "Tile, Inc."
        0x002B -> "Polar Electro"
        0x0211 -> "Fitbit, Inc."
        0x039E -> "OPPO"
        0x0499 -> "Razer Inc."
        0x0986 -> "Nothing Technology"
        0x0948 -> "Motorola Mobility"
        0x424E -> "Ninebot / Segway"
        0x4E42 -> "Ninebot"
        else -> null
    }

    private fun load(context: Context): Map<String, String> {
        return runCatching {
            val map = HashMap<String, String>(40_000)
            context.assets.open("ble/nmap-mac-prefixes.txt").bufferedReader().useLines { lines ->
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
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}
