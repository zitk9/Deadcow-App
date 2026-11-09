package com.droid.dolphy.scooter

data class ScooterDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val batteryPercent: Int? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val statusMessage: String? = null,
    val brandOrCategory: String = "Trottinette BLE",
    val isRentalFleet: Boolean = false,
    val rawAdvertisement: String? = null,
)
