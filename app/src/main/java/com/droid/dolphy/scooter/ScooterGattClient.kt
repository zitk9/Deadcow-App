package com.droid.dolphy.scooter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class ScooterGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onBattery(percent: Int)
        fun onCommandResult(ok: Boolean, label: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val ready = AtomicBoolean(false)
    private var isDisconnecting = false
    private var readyTimeoutRunnable: Runnable? = null

    fun connect() {
        close()
        isDisconnecting = false
        ready.set(false)
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        } catch (t: Throwable) {
            main.post { listener.onError("Erreur connexion: ${t.message}") }
        }
    }

    fun disconnect() {
        isDisconnecting = true
        ready.set(false)
        cancelReadyTimeout()
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        close()
        main.post { listener.onDisconnected(null) }
    }

    fun close() {
        ready.set(false)
        cancelReadyTimeout()
        writeChar = null
        notifyChar = null
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private fun cancelReadyTimeout() {
        readyTimeoutRunnable?.let { main.removeCallbacks(it) }
        readyTimeoutRunnable = null
    }

    fun sendTuningPayload(payload: ByteArray, label: String) = send(payload, label)

    fun send(payload: ByteArray, label: String, silent: Boolean = false) {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null || !ready.get()) {
            if (!silent) main.post { listener.onCommandResult(false, "$label (non connecté)") }
            return
        }

        executor.execute {
            try {
                val currentGatt = gatt ?: return@execute
                val currentChar = writeChar ?: return@execute

                val writeType = if ((currentChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

                var offset = 0
                var allOk = true

                while (offset < payload.size) {
                    val end = minOf(offset + 20, payload.size)
                    val chunk = payload.copyOfRange(offset, end)

                    val writeSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        currentGatt.writeCharacteristic(currentChar, chunk, writeType) ==
                            android.bluetooth.BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        currentChar.value = chunk
                        @Suppress("DEPRECATION")
                        currentChar.writeType = writeType
                        @Suppress("DEPRECATION")
                        currentGatt.writeCharacteristic(currentChar)
                    }

                    if (!writeSuccess) {
                        allOk = false
                        break
                    }
                    offset = end
                    if (offset < payload.size) {
                        Thread.sleep(25)
                    }
                }

                if (!silent) {
                    main.post { listener.onCommandResult(allOk, label) }
                }
            } catch (t: Throwable) {
                if (!silent) {
                    main.post { listener.onError(t.message ?: "Échec d'écriture UART") }
                }
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Stabilisation courte avant la découverte des services (évite les conflits MTU / 133)
                        main.postDelayed({
                            try {
                                g.discoverServices()
                            } catch (e: Exception) {
                                main.post { listener.onError("Erreur découverte: ${e.message}") }
                            }
                        }, 150)
                    } else {
                        main.post { listener.onError("Échec connexion GATT (code=$status)") }
                        close()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    ready.set(false)
                    cancelReadyTimeout()
                    main.post { listener.onDisconnected(if (isDisconnecting) null else "Déconnecté (status=$status)") }
                    close()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { listener.onError("Services introuvables (status=$status)") }
                disconnect()
                return
            }

            // 1. Essai Nordic UART (Ninebot standard)
            var service = g.getService(ScooterProtocol.SERVICE_UUID_NORDIC_UART)
            if (service != null) {
                writeChar = service.getCharacteristic(ScooterProtocol.WRITE_UUID_NORDIC_UART)
                notifyChar = service.getCharacteristic(ScooterProtocol.NOTIFY_UUID_NORDIC_UART)
            }

            // 2. Essai Xiaomi UART (M365 / Pro / 1S / Essential)
            if (writeChar == null) {
                val xiaomiService = g.getService(ScooterProtocol.SERVICE_UUID_XIAOMI_UART)
                if (xiaomiService != null) {
                    writeChar = xiaomiService.getCharacteristic(ScooterProtocol.WRITE_UUID_XIAOMI_UART)
                    notifyChar = xiaomiService.getCharacteristic(ScooterProtocol.NOTIFY_UUID_XIAOMI_UART)
                }
            }

            // 3. Essai Ninebot Custom UART
            if (writeChar == null) {
                val ninebotService = g.getService(ScooterProtocol.SERVICE_UUID_NINEBOT_CUSTOM)
                if (ninebotService != null) {
                    writeChar = ninebotService.getCharacteristic(ScooterProtocol.WRITE_UUID_NINEBOT_CUSTOM)
                        ?: ninebotService.getCharacteristic(ScooterProtocol.NOTIFY_UUID_NINEBOT_CUSTOM)
                    notifyChar = ninebotService.getCharacteristic(ScooterProtocol.NOTIFY_UUID_NINEBOT_CUSTOM)
                }
            }

            // 4. Essai Série Générique HM-10 / CC2540
            if (writeChar == null) {
                val hm10Service = g.getService(ScooterProtocol.SERVICE_UUID_HM10)
                if (hm10Service != null) {
                    val ch = hm10Service.getCharacteristic(ScooterProtocol.CHAR_UUID_HM10)
                    if (ch != null) {
                        writeChar = ch
                        notifyChar = ch
                    }
                }
            }

            // 5. Recherche universelle dynamique parmi tous les services découverts
            if (writeChar == null) {
                for (s in g.services) {
                    for (c in s.characteristics) {
                        val props = c.properties
                        val canWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val canNotify = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                        if (writeChar == null && canWrite) {
                            writeChar = c
                        }
                        if (notifyChar == null && canNotify) {
                            notifyChar = c
                        }
                    }
                    if (writeChar != null && notifyChar != null) break
                }
            }

            if (writeChar == null) {
                main.post { listener.onError("Canal UART d'écriture non trouvé sur cet équipement") }
                disconnect()
                return
            }

            // Configuration de la notification / télémétrie de retour
            val n = notifyChar
            if (n != null) {
                try {
                    g.setCharacteristicNotification(n, true)
                    val cccd = n.getDescriptor(CCCD)
                    if (cccd != null) {
                        val descriptorValue = if ((n.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        } else {
                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        }
                        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(cccd, descriptorValue) == android.bluetooth.BluetoothStatusCodes.SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = descriptorValue
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(cccd)
                        }
                        if (!ok) {
                            markReadyAndConnected()
                        }
                    } else {
                        markReadyAndConnected()
                    }
                } catch (_: Exception) {
                    markReadyAndConnected()
                }
            } else {
                markReadyAndConnected()
            }

            // Timeout de sécurité : marquer prêt après 1,2s quoi qu'il arrive
            readyTimeoutRunnable = Runnable { markReadyAndConnected() }
            main.postDelayed(readyTimeoutRunnable!!, 1200)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            cancelReadyTimeout()
            markReadyAndConnected()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onNotify(value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotify(value)
        }
    }

    private fun markReadyAndConnected() {
        cancelReadyTimeout()
        if (ready.compareAndSet(false, true)) {
            main.post { listener.onConnected() }
            // Demande automatique de télémétrie (batterie / état)
            main.postDelayed({
                send(ScooterProtocol.COMMAND_QUERY_STATUS, "Statut", silent = true)
            }, 300)
        }
    }

    private fun onNotify(chunk: ByteArray) {
        if (chunk.size < 5) return
        try {
            for (i in 0 until chunk.size - 4) {
                val b0 = chunk[i].toInt() and 0xFF
                val b1 = chunk[i + 1].toInt() and 0xFF
                // Ninebot UART header 0x55 0xAA ou 0x5A 0xA5
                if ((b0 == 0x55 && b1 == 0xAA) || (b0 == 0x5A && b1 == 0xA5)) {
                    val len = chunk[i + 2].toInt() and 0xFF
                    if (i + 3 + len <= chunk.size && i + 5 < chunk.size) {
                        val cmd = chunk[i + 5].toInt() and 0xFF
                        if (cmd == 0x1A && i + 6 < chunk.size) {
                            val battery = chunk[i + 6].toInt() and 0xFF
                            if (battery in 0..100) {
                                main.post { listener.onBattery(battery) }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
