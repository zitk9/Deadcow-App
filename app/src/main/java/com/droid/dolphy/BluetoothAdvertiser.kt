package com.droid.dolphy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.droid.dolphy.plugin.PluginBluetoothHooks
import org.json.JSONObject

class BluetoothAdvertiser {

<<<<<<< HEAD
    private val bluetoothAdapter get() = BluetoothHelper.bluetoothAdapter
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser
=======
    private val bluetoothAdapter = BluetoothHelper.bluetoothAdapter
    private val advertiser get() = try { bluetoothAdapter?.bluetoothLeAdvertiser } catch (_: SecurityException) { null }
>>>>>>> upstream/main

    private var currentLegacyCallback: AdvertiseCallback? = null
    private var currentExtCallback: AdvertisingSetCallback? = null

<<<<<<< HEAD
    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
=======
>>>>>>> upstream/main
    fun advertise(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData? = null,
        forceLegacy: Boolean = true,
        connectable: Boolean = false
    ) {
        val pluginDecision = PluginBluetoothHooks.interceptAdvertising(
            advertiseData,
            scanResponse,
            JSONObject().put("source", "ble_spam"),
        )
        if (pluginDecision.skipNative) return
        val effectiveData = pluginDecision.advertiseData
        val effectiveScanResponse = pluginDecision.scanResponse

        // BLE pair/setup protocols (Samsung Watch/Buds, FastPair, SwiftPair, Continuity)
        // require standard legacy Bluetooth 4.x advertisements (channels 37/38/39).
        // Extended advertising with setLegacyMode(false) is only used when explicitly requested
        // or when payload exceeds standard 31-byte legacy limit.
        if (!forceLegacy && Helper.canUseExtendedAdvertising()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                advertiseExtended(effectiveData, effectiveScanResponse, connectable)
                return
            }
        }
        advertiseLegacy(effectiveData, effectiveScanResponse, connectable)
    }

<<<<<<< HEAD
    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun advertiseLegacy(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        connectable: Boolean = false
    ) {
=======
    private fun advertiseLegacy(advertiseData: AdvertiseData, scanResponse: AdvertiseData?, connectable: Boolean = false) {
>>>>>>> upstream/main
        val adv = advertiser ?: run {
            Helper.log("BluetoothLeAdvertiser is null - Bluetooth disabled or peripheral mode unsupported")
            return
        }

        // Clean up any previously active advertisement first
        stopAdvertisingInternal()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Helper.log("Legacy advertising started successfully (connectable=$connectable, hasScanResp=${scanResponse != null})")
            }

            override fun onStartFailure(errorCode: Int) {
                Helper.log("Legacy advertising failed with errorCode=$errorCode")
                if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED || errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                    synchronized(this@BluetoothAdvertiser) {
                        currentLegacyCallback = null
                    }
                }
            }
        }

        try {
            adv.startAdvertising(settings, advertiseData, scanResponse, callback)
            currentLegacyCallback = callback
            BleSpamRuntime.trackSentPackets()
        } catch (e: Exception) {
            Helper.log("startAdvertising exception: ${e.message}")
            currentLegacyCallback = null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
<<<<<<< HEAD
    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun advertiseExtended(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        connectable: Boolean = false
    ) {
=======
    private fun advertiseExtended(advertiseData: AdvertiseData, connectable: Boolean = false) {
>>>>>>> upstream/main
        val adv = advertiser ?: run {
            Helper.log("BluetoothLeAdvertiser is null (extended)")
            return
        }

        stopAdvertisingInternal()

        val hasScanResponse = scanResponse != null
        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(true) // Keep legacy compatibility so watches/phones can detect frames
            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setConnectable(connectable)
            .setScannable(hasScanResponse)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
            .build()

        val extCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: android.bluetooth.le.AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == ADVERTISE_SUCCESS) {
                    Helper.log("Extended advertising set started (tx=$txPower dBm)")
                } else {
                    Helper.log("Extended advertising set failed: $status - falling back to legacy")
                    synchronized(this@BluetoothAdvertiser) {
                        currentExtCallback = null
                    }
                    advertiseLegacy(advertiseData, scanResponse, connectable)
                }
            }
        }

        try {
            adv.startAdvertisingSet(params, advertiseData, scanResponse, null, null, extCallback)
            currentExtCallback = extCallback
            BleSpamRuntime.trackSentPackets()
        } catch (e: Exception) {
            Helper.log("startAdvertisingSet error: ${e.message} - falling back to legacy")
            currentExtCallback = null
            advertiseLegacy(advertiseData, scanResponse, connectable)
        }
    }

<<<<<<< HEAD
    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
=======
>>>>>>> upstream/main
    fun stopAdvertising() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.advertising.stop", JSONObject().put("source", "ble_spam"))
        if (pluginDecision.cancelled) return
        if (pluginDecision.handled) {
            currentLegacyCallback = null
            currentExtCallback = null
            return
        }
<<<<<<< HEAD
        stopAdvertisingInternal()
    }
=======
        if (extendedCallbackActive) {
            extCallback?.let {
                try {
                    val adv = advertiser
                    try { adv?.stopAdvertisingSet(it) } catch (error: SecurityException) { Log.w("BLESpam", "Advertising permission revoked", error) }
>>>>>>> upstream/main

    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertisingInternal() {
        val adv = advertiser

        currentExtCallback?.let {
            try {
<<<<<<< HEAD
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    adv?.stopAdvertisingSet(it)
                }
                Helper.log("Extended advertising stopped")
            } catch (e: Exception) {
                Log.e("BLESpam", "Error stopping extended advertising: ${e.message}")
            }
            currentExtCallback = null
        }

        currentLegacyCallback?.let {
            try {
                adv?.stopAdvertising(it)
=======
                try { advertiser?.stopAdvertising(advertiseCallback) } catch (error: SecurityException) { Log.w("BLESpam", "Advertising permission revoked", error) }
>>>>>>> upstream/main
                Helper.log("Legacy advertising stopped")
            } catch (e: Exception) {
                Log.e("BLESpam", "Error stopping legacy advertising: ${e.message}")
            }
            currentLegacyCallback = null
        }
    }
}
