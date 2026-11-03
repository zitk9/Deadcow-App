package com.droid.dolphy

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

object BluetoothHelper {
    val bluetoothAdapter: BluetoothAdapter?
        get() {
            return try {
                val app = runCatching { DolphyApplication.instance }.getOrNull()
                val manager = app?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            } catch (_: Throwable) {
                try {
                    BluetoothAdapter.getDefaultAdapter()
                } catch (_: Throwable) {
                    null
                }
            }
        }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val canAdvertise: Boolean
        get() {
            val adapter = bluetoothAdapter ?: return false
            return adapter.isEnabled && (adapter.isMultipleAdvertisementSupported || adapter.bluetoothLeAdvertiser != null)
        }
}


