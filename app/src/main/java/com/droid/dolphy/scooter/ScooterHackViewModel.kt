package com.droid.dolphy.scooter

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

@SuppressLint("MissingPermission")
class ScooterHackViewModel(app: Application) : AndroidViewModel(app) {

    private val _devices = MutableStateFlow<List<ScooterDevice>>(emptyList())
    val devices: StateFlow<List<ScooterDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()

    private val _showAllDevices = MutableStateFlow(false)
    val showAllDevices: StateFlow<Boolean> = _showAllDevices.asStateFlow()

    private val deviceMap = linkedMapOf<String, ScooterDevice>()
    private var adapter: BluetoothAdapter? = null
    private var gattClient: ScooterGattClient? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address ?: return
            val match = classifyDevice(result)

            // Si le filtre strict est actif et que l'appareil n'est pas identifié comme trottinette ou flotte
            if (!_showAllDevices.value && match == null) return

            val resolved = com.droid.dolphy.BluetoothDeviceResolver.resolve(result.device, result.scanRecord)
            val name = if (resolved.isUserNamed) {
                resolved.name
            } else if (!resolved.manufacturer.isNullOrBlank()) {
                "${resolved.manufacturer} Scooter"
            } else if (match?.defaultName != null) {
                match.defaultName
            } else {
                "Trottinette (${if (address.length >= 5) address.takeLast(5) else "BLE"})"
            }

            val rssi = result.rssi
            val existing = deviceMap[address]
            val brand = match?.brand ?: existing?.brandOrCategory ?: "Périphérique BLE"
            val isFleet = match?.isRentalFleet ?: (existing?.isRentalFleet ?: false)

            deviceMap[address] = (existing ?: ScooterDevice(
                address = address,
                name = name,
                rssi = rssi,
                brandOrCategory = brand,
                isRentalFleet = isFleet,
            )).copy(
                name = name,
                rssi = rssi,
                brandOrCategory = brand,
                isRentalFleet = isFleet,
                isConnected = existing?.isConnected == true || _connectedAddress.value == address,
                isConnecting = existing?.isConnecting == true,
                batteryPercent = existing?.batteryPercent,
                statusMessage = existing?.statusMessage,
            )
            publish()
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    fun ensureAdapter(context: Context) {
        if (adapter == null) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            adapter = bm?.adapter
        }
    }

    fun toggleFilterMode() {
        _showAllDevices.value = !_showAllDevices.value
        publish()
    }

    fun clearDevices() {
        val connected = _connectedAddress.value
        if (connected != null) {
            val active = deviceMap[connected]
            deviceMap.clear()
            if (active != null) {
                deviceMap[connected] = active
            }
        } else {
            deviceMap.clear()
        }
        publish()
    }

    fun startScan(context: Context) {
        ensureAdapter(context)
        val a = adapter ?: return
        if (!a.isEnabled) return
        if (_isScanning.value) return
        val scanner = a.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Android 12+ avec neverForLocation nécessite une liste non vide de ScanFilter.
        // Un ScanFilter vide autorise tous les paquets BLE sans être bloqué par le système.
        val openFilters = listOf(ScanFilter.Builder().build())

        try {
            scanner.startScan(openFilters, settings, scanCallback)
            _isScanning.value = true
        } catch (_: Exception) {
            try {
                scanner.startScan(scanCallback)
                _isScanning.value = true
            } catch (_: Exception) {
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _isScanning.value = false
    }

    fun toggleConnection(context: Context, scooter: ScooterDevice) {
        if (_connectedAddress.value == scooter.address || scooter.isConnected) {
            disconnect()
            return
        }
        connect(context, scooter)
    }

    fun connect(context: Context, scooter: ScooterDevice) {
        ensureAdapter(context)
        val a = adapter ?: return
        disconnect(silent = true)
        updateDevice(scooter.address) {
            it.copy(isConnecting = true, statusMessage = "Connexion en cours…", isConnected = false)
        }
        val remote = try {
            a.getRemoteDevice(scooter.address)
        } catch (_: Exception) {
            updateDevice(scooter.address) {
                it.copy(isConnecting = false, statusMessage = "Adresse MAC invalide")
            }
            return
        }
        gattClient = ScooterGattClient(
            context = context.applicationContext,
            device = remote,
            listener = object : ScooterGattClient.Listener {
                override fun onConnected() {
                    _connectedAddress.value = scooter.address
                    updateDevice(scooter.address) {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            statusMessage = if (it.isRentalFleet) {
                                "Connecté BLE (Flotte sécurisée IoT)"
                            } else {
                                "Connecté (UART Prêt)"
                            },
                        )
                    }
                }

                override fun onDisconnected(reason: String?) {
                    if (_connectedAddress.value == scooter.address) {
                        _connectedAddress.value = null
                    }
                    updateDevice(scooter.address) {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            statusMessage = reason ?: "Déconnecté",
                        )
                    }
                    gattClient = null
                }

                override fun onBattery(percent: Int) {
                    updateDevice(scooter.address) { it.copy(batteryPercent = percent) }
                }

                override fun onCommandResult(ok: Boolean, label: String) {
                    updateDevice(scooter.address) {
                        it.copy(statusMessage = if (ok) "$label ✓" else "$label : échec")
                    }
                }

                override fun onError(message: String) {
                    updateDevice(scooter.address) {
                        it.copy(isConnecting = false, statusMessage = message)
                    }
                }
            },
        )
        gattClient?.connect()
    }

    fun disconnect(silent: Boolean = false) {
        val addr = _connectedAddress.value
        gattClient?.disconnect()
        gattClient?.close()
        gattClient = null
        _connectedAddress.value = null
        if (addr != null && !silent) {
            updateDevice(addr) {
                it.copy(isConnected = false, isConnecting = false, statusMessage = "Déconnecté")
            }
        }
    }

    fun sendTuningPayload(payload: ByteArray, label: String) {
        gattClient?.sendTuningPayload(payload, label)
    }

    private fun updateDevice(address: String, transform: (ScooterDevice) -> ScooterDevice) {
        val cur = deviceMap[address] ?: return
        deviceMap[address] = transform(cur)
        publish()
    }

    private fun publish() {
        val list = if (_showAllDevices.value) {
            deviceMap.values.toList()
        } else {
            deviceMap.values.toList()
        }
        _devices.value = list
            .sortedWith(compareByDescending<ScooterDevice> { it.isConnected }
                .thenByDescending { it.isRentalFleet }
                .thenByDescending { it.rssi })
    }

    override fun onCleared() {
        stopScan()
        disconnect(silent = true)
        super.onCleared()
    }

    private data class DeviceClassification(
        val brand: String,
        val isRentalFleet: Boolean,
        val defaultName: String? = null,
    )

    companion object {
        // Préfixes et mots-clés des flottes de trottinettes en libre-service
        private val RENTAL_FLEET_HINTS = listOf(
            "lime",
            "tier",
            "dott",
            "bolt",
            "bird",
            "voi",
            "circ",
            "wind",
            "pony",
            "omni",
            "okai",
            "comodule",
            "teltonika",
        )

        // Préfixes et mots-clés des trottinettes personnelles grand public
        private val PERSONAL_SCOOTER_HINTS = listOf(
            "miscooter",
            "mi scooter",
            "xiaomi",
            "ninebot",
            "segway",
            "m365",
            "mi electric",
            "mipro",
            "mijia",
            "nb-",
            "max g",
            "g30",
            "f20",
            "f25",
            "f30",
            "f40",
            "es1",
            "es2",
            "es4",
            "e22",
            "e25",
            "e45",
            "d18",
            "d28",
            "d38",
            "p65",
            "p100",
            "gt1",
            "gt2",
            "kukirin",
            "kugoo",
            "dualtron",
            "inokim",
            "vsett",
            "kaabo",
            "navee",
            "joyor",
            "niu",
            "ducati",
            "pure electric",
            "speedtrott",
            "scooter",
            "trottinette",
            "trotti",
            "escooter",
            "e-scooter",
            "patinete",
        )

        private fun classifyDevice(result: ScanResult): DeviceClassification? {
            val record = result.scanRecord
            val rawName = (result.device.name ?: record?.deviceName ?: "").trim()
            val lowerName = rawName.lowercase(Locale.ROOT)

            // 1. Détection des flottes de libre-service
            for (hint in RENTAL_FLEET_HINTS) {
                if (lowerName.contains(hint)) {
                    val brandName = hint.replaceFirstChar { it.uppercase() }
                    return DeviceClassification(
                        brand = "$brandName (Libre-Service)",
                        isRentalFleet = true,
                        defaultName = rawName.ifBlank { "$brandName Fleet Scooter" },
                    )
                }
            }

            // Vérification UUID Omni IoT (boîtiers de flottes)
            val uuids = record?.serviceUuids
            if (uuids != null && uuids.any { it.uuid == ScooterProtocol.SERVICE_UUID_OMNI_IOT }) {
                return DeviceClassification(
                    brand = "Flotte Libre-Service (Omni IoT)",
                    isRentalFleet = true,
                    defaultName = rawName.ifBlank { "Omni Fleet IoT" },
                )
            }

            // 2. Détection Xiaomi Mi Scooter (M365, 1S, Pro, Pro 2, Essential, etc.)
            val isXiaomiName = lowerName.contains("xiaomi") ||
                lowerName.contains("miscooter") ||
                lowerName.contains("mi scooter") ||
                lowerName.contains("m365") ||
                lowerName.contains("mijia")

            val isXiaomiUuid = uuids != null && uuids.any {
                it.uuid == ScooterProtocol.SERVICE_UUID_XIAOMI_MIBEACON ||
                    it.uuid == ScooterProtocol.SERVICE_UUID_XIAOMI_UART
            }

            val mfg = record?.manufacturerSpecificData
            val isXiaomiMfg = mfg != null && mfg.indexOfKey(ScooterProtocol.XIAOMI_MANUFACTURER_ID) >= 0

            if (isXiaomiName || isXiaomiUuid || isXiaomiMfg) {
                return DeviceClassification(
                    brand = "Xiaomi Mi Scooter",
                    isRentalFleet = false,
                    defaultName = rawName.ifBlank { "Xiaomi Scooter" },
                )
            }

            // 3. Détection Ninebot / Segway (Max G30, ES1-ES4, E22-E45, F-Series, etc.)
            val isNinebotName = lowerName.contains("ninebot") ||
                lowerName.contains("segway") ||
                lowerName.contains("kickscooter") ||
                lowerName.startsWith("nb-") ||
                lowerName.contains("max g") ||
                lowerName.contains("g30") ||
                lowerName.contains("es1") ||
                lowerName.contains("es2") ||
                lowerName.contains("es4")

            val isNinebotMfg = mfg != null && (
                mfg.indexOfKey(ScooterProtocol.NINEBOT_MANUFACTURER_ID) >= 0 ||
                    mfg.indexOfKey(ScooterProtocol.NINEBOT_ALT_MANUFACTURER_ID) >= 0 ||
                    mfg.indexOfKey(ScooterProtocol.SEGWAY_MANUFACTURER_ID) >= 0
                )

            val isNinebotUuid = uuids != null && uuids.any {
                it.uuid == ScooterProtocol.SERVICE_UUID_NORDIC_UART ||
                    it.uuid == ScooterProtocol.SERVICE_UUID_NINEBOT_CUSTOM
            }

            // Vérification octets bruts "NB "
            var hasNinebotHeader = false
            val rawBytes = record?.bytes
            if (rawBytes != null && rawBytes.size > 6) {
                for (i in 0 until rawBytes.size - 3) {
                    if ((rawBytes[i].toInt() and 0xFF) == 0x4E &&
                        (rawBytes[i + 1].toInt() and 0xFF) == 0x42 &&
                        (rawBytes[i + 2].toInt() and 0xFF) == 0x20
                    ) {
                        hasNinebotHeader = true
                        break
                    }
                }
            }

            if (isNinebotName || isNinebotMfg || isNinebotUuid || hasNinebotHeader) {
                return DeviceClassification(
                    brand = "Ninebot / Segway",
                    isRentalFleet = false,
                    defaultName = rawName.ifBlank { "Ninebot Scooter" },
                )
            }

            // 4. Détection des autres marques de trottinettes personnelles
            for (hint in PERSONAL_SCOOTER_HINTS) {
                if (lowerName.contains(hint)) {
                    val brandName = hint.replaceFirstChar { it.uppercase() }
                    return DeviceClassification(
                        brand = "$brandName Scooter",
                        isRentalFleet = false,
                        defaultName = rawName.ifBlank { "$brandName Scooter" },
                    )
                }
            }

            return null
        }
    }
}
