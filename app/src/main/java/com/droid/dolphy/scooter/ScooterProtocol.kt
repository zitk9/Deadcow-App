package com.droid.dolphy.scooter

import java.util.UUID

object ScooterProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val WRITE_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    const val NINEBOT_MANUFACTURER_ID = 0x424E

    // Commands directes Ninebot / Xiaomi UART
    val COMMAND_LOCK = byteArrayOf(0x55, 0xAA.toByte(), 0x03, 0x20, 0x01, 0x71, 0x01, 0x69.toByte(), 0xFF.toByte())
    val COMMAND_UNLOCK = byteArrayOf(0x55, 0xAA.toByte(), 0x03, 0x20, 0x01, 0x71, 0x00, 0x6A.toByte(), 0xFF.toByte())
    val COMMAND_BEEP = byteArrayOf(0x55, 0xAA.toByte(), 0x03, 0x20, 0x01, 0x72, 0x01, 0x68.toByte(), 0xFF.toByte())
    val COMMAND_LIGHTS_TOGGLE = byteArrayOf(0x55, 0xAA.toByte(), 0x03, 0x20, 0x01, 0x7D, 0x02, 0x5C.toByte(), 0xFF.toByte())
    val COMMAND_SPORT_SPEED_30 = byteArrayOf(0x55, 0xAA.toByte(), 0x04, 0x20, 0x01, 0x7C, 0x1E, 0x00, 0x40.toByte(), 0xFF.toByte())
    val COMMAND_ECO_SPEED_15 = byteArrayOf(0x55, 0xAA.toByte(), 0x04, 0x20, 0x01, 0x7C, 0x0F, 0x00, 0x4F.toByte(), 0xFF.toByte())
    val COMMAND_SPEED_100 = buildSpeedCommand(100)
    val COMMAND_QUERY_STATUS = byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x20, 0x01, 0x1A, 0xC3.toByte(), 0xFF.toByte())

    // Payloads Firmware Tuning
    val PAYLOAD_MAX_2G = byteArrayOf(0x55, 0xAB.toByte(), 0x4D, 0x41, 0x58, 0x32, 0x53, 0x63, 0x6F, 0x6F, 0x74, 0x65, 0x72, 0x5F, 0x31)
    val PAYLOAD_F2 = byteArrayOf(0x55, 0xAB.toByte(), 0x46, 0x32, 0x53, 0x63, 0x6F, 0x6F, 0x74, 0x65, 0x72, 0x5F, 0x31)
    val PAYLOAD_GEN_1 = byteArrayOf(0x5A, 0xA5.toByte(), 0x00, 0x4B, 0x48, 0x94.toByte(), 0xE3.toByte(), 0x3A, 0x91.toByte(), 0xE0.toByte(), 0x32, 0x7E, 0xC2.toByte())
    val PAYLOAD_GEN_2 = byteArrayOf(0x5A, 0xA5.toByte(), 0x00, 0x4B, 0x48, 0x94.toByte(), 0xE3.toByte(), 0x3A, 0x91.toByte(), 0xE0.toByte(), 0x43, 0x3E, 0xC2.toByte())
    val PAYLOAD_GEN_3 = byteArrayOf(0x5A, 0xA5.toByte(), 0x00, 0x4B, 0x48, 0x94.toByte(), 0xE3.toByte(), 0x3A, 0x91.toByte(), 0xE0.toByte(), 0x42, 0x7E, 0xC4.toByte())

    fun buildSpeedCommand(speedKmh: Int): ByteArray {
        val clamped = speedKmh.coerceIn(10, 100)
        val len = 0x04
        val target = 0x20
        val source = 0x01
        val cmd = 0x7C
        val spLow = clamped and 0xFF
        val spHigh = (clamped shr 8) and 0xFF
        val sum = len + target + source + cmd + spLow + spHigh
        val checksum = (0xFFFF - sum) and 0xFFFF
        val csLow = checksum and 0xFF
        val csHigh = (checksum shr 8) and 0xFF
        return byteArrayOf(
            0x55,
            0xAA.toByte(),
            len.toByte(),
            target.toByte(),
            source.toByte(),
            cmd.toByte(),
            spLow.toByte(),
            spHigh.toByte(),
            csLow.toByte(),
            csHigh.toByte()
        )
    }
}
