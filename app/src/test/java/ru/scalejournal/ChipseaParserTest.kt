package ru.scalejournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.scalejournal.ble.ChipseaParser

class ChipseaParserTest {

    private val frameLB = byteArrayOf(
        0x06, 0x03, 0xE5.toByte(), 0x07, 0x01, 0x30, 0x00, 0x00, 0x00,
        0x88.toByte(), 0x13, 0x84.toByte(), 0x08, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00, 0x00,
    )

    // год 2019 (0x07E3) вне 2020..2099; месяц, день и время валидны
    private val frameKG = byteArrayOf(
        0x06, 0x03, 0xE3.toByte(), 0x07, 0x01, 0x01, 0x00, 0x00, 0x00,
        0x88.toByte(), 0x13, 0xDD.toByte(), 0x03, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00,
    )

    @Test
    fun lockedFrameKgDecodesWeightImpedance() {
        val r = ChipseaParser.parseLocked(frameKG)!!
        assertEquals(98.9, r.weightKg, 0.001)
        assertEquals(500.0, r.impedanceOhm!!, 0.001)
        assertNull(r.deviceTimeMillis)
    }

    @Test
    fun lockedFrameLbConvertsToKg() {
        val r = ChipseaParser.parseLocked(frameLB)!!
        assertEquals(98.88, r.weightKg, 0.01)
        assertEquals(500.0, r.impedanceOhm!!, 0.001)
    }

    @Test
    fun broadcastIssue930Decodes() {
        val payload = byteArrayOf(0x02, 0x04, 0xDB.toByte(), 0x03, 0, 0, 0, 0, 0, 0, 0, 0)
        val r = ChipseaParser.parseBroadcast(payload)!!
        assertEquals(98.7, r.weightKg, 0.001)
    }

    @Test
    fun weightOnlyFlagsAccepted() {
        val frame = ByteArray(16)
        frame[0] = 0x02.toByte()
        frame[1] = 0x00.toByte()
        frame[11] = 0x34.toByte()
        frame[12] = 0x30.toByte()
        val r = ChipseaParser.parseLocked(frame)!!
        assertEquals(123.4, r.weightKg, 0.001)
        assertNull(r.impedanceOhm)
    }

    @Test
    fun garbageRejected() {
        assertNull(ChipseaParser.parseLocked(ByteArray(16)))
        assertNull(ChipseaParser.parseLocked(ByteArray(4)))
        assertNull(ChipseaParser.parseBroadcast(ByteArray(3)))
    }

    @Test
    fun outOfRangeWeightRejected() {
        val frame = ByteArray(16)
        frame[0] = 0x02.toByte()
        frame[11] = 0xFF.toByte()
        frame[12] = 0xFF.toByte()
        assertNull(ChipseaParser.parseLocked(frame))
    }
}
