package com.droid.dolphy

data class EasySetupDevice(
    val value: String,
    val name: String,
    val deviceType: Type
) {
    enum class Type { BUDS, WATCH }

    fun toManufacturerData(): String {
        val clean = value.removePrefix("0x").removePrefix("0X").trim().uppercase()
        return when (deviceType) {
            Type.WATCH -> {
                val watchId = if (clean.length % 2 != 0) "0$clean" else clean
                "010002000101FF000043$watchId"
            }
            Type.BUDS -> {
                val budsVal = clean.padEnd(6, '0').take(6)
                "42098102141503210109${budsVal.take(4)}01${budsVal.drop(4)}063C948E00000000C700"
            }
        }
    }
}


