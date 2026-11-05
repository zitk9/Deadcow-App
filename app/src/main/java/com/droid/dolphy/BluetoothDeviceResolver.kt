package com.droid.dolphy

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import com.droid.dolphy.ble.smarthome.BleVendorDb
import com.droid.dolphy.bluetooth.whisperpair.KnownDevices
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ResolvedDevice(
    val name: String,
    val manufacturer: String?,
    val category: String,
    val isUserNamed: Boolean,
    val detailSubtitle: String
)

object BluetoothDeviceResolver {
    private val cache = ConcurrentHashMap<String, ResolvedDevice>()

    private val GENERIC_NAMES = setOf(
        "unknown",
        "unknown device",
        "unknown audio",
        "inconnu",
        "appareil inconnu",
        "appareil ble",
        "ble device",
        "bluetooth device",
        "périphérique bluetooth",
        "périphérique ble",
        "null",
        "undefined",
        "n/a",
        "no permission",
        "<unknown ssid>",
        "smart light"
    )

    private val FAST_PAIR_SERVICE_UUID = ParcelUuid.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")

    fun isGenericUnknown(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val trimmed = name.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower in GENERIC_NAMES) return true
        if (lower.startsWith("unknown") || lower.startsWith("inconnu")) return true
        if (trimmed.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$"))) return true
        return false
    }

    fun getCached(address: String?): ResolvedDevice? {
        if (address.isNullOrBlank()) return null
        return cache[address.uppercase(Locale.ROOT)]
    }

    fun registerScanResult(result: ScanResult, context: Context? = null): ResolvedDevice {
        val device = result.device
        val scanRecord = result.scanRecord
        return resolve(
            device = device,
            scanRecord = scanRecord,
            extraName = scanRecord?.deviceName,
            context = context
        )
    }

    fun registerClassicDevice(
        device: BluetoothDevice,
        extraName: String?,
        btClass: BluetoothClass?,
        context: Context? = null
    ): ResolvedDevice {
        return resolveClassic(device, extraName, btClass, context)
    }

    fun resolveClassic(
        device: BluetoothDevice,
        extraName: String?,
        btClass: BluetoothClass?,
        context: Context? = null
    ): ResolvedDevice {
        return resolve(
            device = device,
            scanRecord = null,
            extraName = extraName,
            btClass = btClass,
            context = context
        )
    }

    fun resolve(
        device: BluetoothDevice,
        scanRecord: ScanRecord? = null,
        extraName: String? = null,
        btClass: BluetoothClass? = null,
        context: Context? = null
    ): ResolvedDevice {
        val address = device.address.orEmpty().uppercase(Locale.ROOT)
        val cached = if (address.isNotBlank()) cache[address] else null

        // 1. Determine user/advertised name
        val rawSystemName = runCatching { device.name }.getOrNull()?.trim()
        val rawExtraName = extraName?.trim()
        val rawRecordName = scanRecord?.deviceName?.trim()

        val advertisedName = listOfNotNull(rawRecordName, rawExtraName, rawSystemName)
            .firstOrNull { !isGenericUnknown(it) }

        // Fast Pair check
        var fastPairModelId: String? = null
        var fastPairDeviceName: String? = null
        var fastPairManufacturer: String? = null
        if (scanRecord != null) {
            val fpData = scanRecord.getServiceData(FAST_PAIR_SERVICE_UUID)
            if (fpData != null && fpData.isNotEmpty()) {
                val firstByte = fpData[0].toInt() and 0xFF
                if (fpData.size == 3 && (firstByte and 0x80) == 0) {
                    fastPairModelId = fpData.joinToString("") { "%02X".format(it) }
                } else if (fpData.size > 3 && (firstByte and 0x80) == 0) {
                    fastPairModelId = fpData.take(3).joinToString("") { "%02X".format(it) }
                }
                fastPairModelId?.let { mid ->
                    fastPairDeviceName = KnownDevices.getDeviceName(mid)
                    fastPairManufacturer = KnownDevices.getManufacturer(mid)
                }
            }
        }

        // 2. Determine Manufacturer / Brand
        var detectedCompany: String? = null
        var appleSubtypeCategory: String? = null
        if (scanRecord != null) {
            val mfgData = scanRecord.manufacturerSpecificData
            if (mfgData != null && mfgData.size() > 0) {
                for (i in 0 until mfgData.size()) {
                    val companyId = mfgData.keyAt(i)
                    val companyName = BleVendorDb.getCompanyIdentifierName(companyId)
                    if (companyName != null) {
                        detectedCompany = companyName
                        if (companyId == 0x004C) {
                            val payload = mfgData.get(companyId)
                            if (payload != null && payload.isNotEmpty()) {
                                appleSubtypeCategory = when (payload[0].toInt() and 0xFF) {
                                    0x02 -> "Balise iBeacon"
                                    0x05 -> "AirDrop"
                                    0x07 -> "Audio (AirPods)"
                                    0x09 -> "AirPlay"
                                    0x10 -> "Appareil Apple"
                                    0x12 -> "Tracker (AirTag/Localiser)"
                                    else -> null
                                }
                            }
                        }
                        break
                    }
                }
            }
        }

        val macVendor = if (address.isNotBlank()) {
            BleVendorDb.getVendorLabel(context, address).takeIf { it != "Unknown" && it.isNotBlank() }
        } else null

        val nameBrand = (advertisedName ?: rawSystemName)?.let { guessBrandFromName(it) }

        val manufacturer = detectedCompany
            ?: fastPairManufacturer
            ?: macVendor
            ?: nameBrand
            ?: cached?.manufacturer

        // 3. Determine Category
        val resolvedBtClass = btClass ?: runCatching { device.bluetoothClass }.getOrNull()
        val majorClass = resolvedBtClass?.majorDeviceClass ?: BluetoothClass.Device.Major.UNCATEGORIZED
        val serviceUuids = scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase(Locale.ROOT) }.orEmpty()

        val category = when {
            appleSubtypeCategory != null -> appleSubtypeCategory
            fastPairDeviceName != null || fastPairModelId != null -> "Audio (Fast Pair)"
            majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio"
            majorClass == BluetoothClass.Device.Major.PHONE -> "Téléphone"
            majorClass == BluetoothClass.Device.Major.COMPUTER -> "Ordinateur"
            majorClass == BluetoothClass.Device.Major.PERIPHERAL -> "Clavier/Souris"
            majorClass == BluetoothClass.Device.Major.WEARABLE -> "Montre/Bracelet"
            serviceUuids.any { it.contains("1812") || it.contains("1124") } -> "Clavier/Souris"
            serviceUuids.any { it.contains("110a") || it.contains("110b") || it.contains("110e") || it.contains("110f") } -> "Audio"
            serviceUuids.any { it.contains("180d") } -> "Santé/Fitness"
            serviceUuids.any { it.contains("feed") } -> "Tracker (Tile)"
            serviceUuids.any { it.contains("fee0") || it.contains("fee1") } -> "Montre/Bracelet"
            advertisedName != null -> guessCategoryFromName(advertisedName)
            rawSystemName != null && !isGenericUnknown(rawSystemName) -> guessCategoryFromName(rawSystemName)
            device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Bluetooth Classic"
            else -> "BLE"
        }

        // 4. Formulate Primary Name
        val isUserNamed = advertisedName != null || fastPairDeviceName != null
        val primaryName = when {
            advertisedName != null -> advertisedName
            fastPairDeviceName != null -> fastPairDeviceName!!
            cached != null && cached.isUserNamed -> cached.name
            manufacturer != null -> "$manufacturer ($category)"
            else -> "Périphérique $category (${if (address.length >= 5) address.takeLast(5) else "BT"})"
        }

        // 5. Formulate Subtitle
        val detailSubtitle = if (manufacturer != null && !primaryName.contains(manufacturer, ignoreCase = true)) {
            "$address • $manufacturer"
        } else {
            "$address • $category"
        }

        val resultDevice = ResolvedDevice(
            name = primaryName,
            manufacturer = manufacturer,
            category = category,
            isUserNamed = isUserNamed,
            detailSubtitle = detailSubtitle
        )

        if (address.isNotBlank()) {
            cache[address] = resultDevice
        }

        return resultDevice
    }

    private fun guessBrandFromName(name: String): String? {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.contains("airpod") || n.contains("iphone") || n.contains("ipad") || n.contains("macbook") || n.contains("apple") -> "Apple, Inc."
            n.contains("galaxy") || n.contains("samsung") -> "Samsung Electronics"
            n.contains("sony") || n.startsWith("wh-") || n.startsWith("wf-") || n.contains("linkbuds") -> "Sony Corporation"
            n.contains("bose") || n.contains("soundlink") || n.contains("quietcomfort") -> "Bose Corporation"
            n.contains("jbl") || n.contains("harman") -> "Harman / JBL"
            n.contains("soundcore") || n.contains("anker") -> "Anker Innovations"
            n.contains("redmi") || n.contains("xiaomi") || n.contains("mi ") || n.contains("poco") -> "Xiaomi Inc."
            n.contains("huawei") || n.contains("freebuds") -> "HUAWEI Technologies"
            n.contains("pixel") || n.contains("google") -> "Google LLC"
            n.contains("oneplus") || n.contains("nord") -> "OnePlus Electronics"
            n.contains("oppo") || n.contains("enco") -> "OPPO"
            n.contains("vivo") -> "Vivo Mobile"
            n.contains("realme") -> "Realme"
            n.contains("nothing") || n.contains("ear (") -> "Nothing Technology"
            n.contains("logitech") || n.contains("logi") || n.contains("mx master") -> "Logitech Inc."
            n.contains("marshall") -> "Marshall / Zound"
            n.contains("sennheiser") -> "Sennheiser"
            n.contains("jabra") || n.contains("elite") -> "GN Audio / Jabra"
            n.contains("skullcandy") -> "Skullcandy"
            n.contains("garmin") -> "Garmin International"
            n.contains("fitbit") -> "Fitbit, Inc."
            n.contains("ninebot") || n.contains("segway") -> "Ninebot / Segway"
            n.contains("bledom") || n.contains("elk-") || n.contains("triones") -> "Smart Light"
            else -> null
        }
    }

    private fun guessCategoryFromName(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        return when {
            n.contains("airpod") || n.contains("buds") || n.contains("head") || n.contains("ear") ||
            n.contains("speaker") || n.contains("sound") || n.contains("audio") || n.contains("casque") ||
            n.contains("écouteur") || n.contains("enceinte") || n.contains("freebuds") ||
            n.startsWith("wh-") || n.startsWith("wf-") || n.contains("linkbuds") -> "Audio"

            n.contains("iphone") || n.contains("galaxy s") || n.contains("galaxy a") || n.contains("pixel") ||
            n.contains("redmi") || n.contains("xiaomi") || n.contains("oneplus") || n.contains("huawei") ||
            n.contains("xperia") -> "Téléphone"

            n.contains("macbook") || n.contains("laptop") || n.contains("pc") || n.contains("desktop") ||
            n.contains("surface") || n.contains("thinkpad") || n.contains("computer") -> "Ordinateur"

            n.contains("keyboard") || n.contains("mouse") || n.contains("souris") || n.contains("clavier") ||
            n.contains("mx master") || n.contains("trackpad") -> "Clavier/Souris"

            n.contains("watch") || n.contains("band") || n.contains("fitbit") || n.contains("garmin") ||
            n.contains("bracelet") || n.contains("montre") -> "Montre/Bracelet"

            n.contains("scooter") || n.contains("trottinette") || n.contains("ninebot") || n.contains("segway") ||
            n.contains("m365") -> "Trottinette"

            n.contains("light") || n.contains("lamp") || n.contains("bulb") || n.contains("bledom") ||
            n.contains("triones") || n.contains("lumiere") || n.contains("led") -> "Lumière"

            n.contains("airtag") || n.contains("tile") || n.contains("tracker") || n.contains("smarttag") -> "Tracker"

            else -> "BLE"
        }
    }
}
