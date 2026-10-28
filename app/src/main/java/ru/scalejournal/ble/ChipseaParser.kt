package ru.scalejournal.ble

import java.util.Calendar

object ChipseaParser {

    const val MFG_ID_VF0 = 0xF0FF

    data class Reading(
        val weightKg: Double,
        val impedanceOhm: Double?,
        val deviceTimeMillis: Long?,
    )

    fun parseLocked(data: ByteArray): Reading? {
        if (data.size < 16) return null
        val flags = u16le(data, 0)
        val singleImpedance = flags == 0x0306
        val dualImpedance = flags == 0x2306
        val weightOnly = flags == 0x0002
        if (!singleImpedance && !dualImpedance && !weightOnly) return null
        val impedance = if (singleImpedance || dualImpedance) u16le(data, 9) / 10.0 else null
        val weight = decodeWeightKg(u16le(data, 11), data[15]) ?: return null
        if (weight < 2.0 || weight > 400.0) return null
        return Reading(weight, impedance, deviceTimeMillis(data))
    }

    fun parseBroadcast(payload: ByteArray): Reading? {
        if (payload.size < 4) return null
        val weight = decodeWeightKg(u16le(payload, 2), payload[1]) ?: return null
        if (weight < 2.0 || weight > 400.0) return null
        return Reading(weight, null, null)
    }

    fun decodeWeightKg(raw: Int, attr: Byte): Double? {
        val a = attr.toInt() and 0xFF
        val unit = (a shr 3) and 0x03
        val decimals = when ((a shr 1) and 0x03) {
            0 -> 2
            1 -> 0
            else -> 1
        }
        val scale = Math.pow(10.0, decimals.toDouble())
        return when (unit) {
            0 -> raw / scale
            1 -> raw / scale * 0.5
            2 -> raw / scale * 0.45359237
            3 -> {
                val stone = ((raw shr 8) and 0xFF).toDouble()
                val pounds = (raw and 0xFF) / scale
                stone * 6.35029318 + pounds * 0.45359237
            }
            else -> null
        }
    }

    private fun deviceTimeMillis(data: ByteArray): Long? {
        val year = u16le(data, 2)
        val month = data[4].toInt() and 0xFF
        val day = data[5].toInt() and 0xFF
        val hour = data[6].toInt() and 0xFF
        val minute = data[7].toInt() and 0xFF
        val second = data[8].toInt() and 0xFF
        if (year !in 2020..2099 || month !in 1..12 || day !in 1..31) return null
        if (hour > 23 || minute > 59 || second > 59) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis
    }

    private fun u16le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
