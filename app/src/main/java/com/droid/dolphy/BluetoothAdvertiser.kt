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

    private val bluetoothAdapter get() = BluetoothHelper.bluetoothAdapter
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var currentLegacyCallback: AdvertiseCallback? = null
    private var currentExtCallback: AdvertisingSetCallback? = null

    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
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
    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun advertiseExtended(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        connectable: Boolean = false
    ) {
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

    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)

    @Synchronized
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun stopAdvertisingInternal() {
        val adv = advertiser

        currentExtCallback?.let {
            try {
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
                Helper.log("Legacy advertising stopped")
            } catch (e: Exception) {
                Log.e("BLESpam", "Error stopping legacy advertising: ${e.message}")
            }
            currentLegacyCallback = null
        }
    }
}
