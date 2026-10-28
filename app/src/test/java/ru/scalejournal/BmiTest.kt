package ru.scalejournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.scalejournal.util.Bmi

class BmiTest {

    @Test
    fun bmiCalculation() {
        assertEquals(30.1, Bmi.value(99.5, 182)!!, 0.1)
        assertEquals(24.2, Bmi.value(75.0, 176)!!, 0.1)
    }

    @Test
    fun bmiCategories() {
        assertEquals("недостаточный вес", Bmi.category(17.0))
        assertEquals("норма", Bmi.category(22.0))
        assertEquals("избыточный вес", Bmi.category(27.0))
        assertEquals("ожирение", Bmi.category(31.0))
    }

    @Test
    fun invalidInputsRejected() {
        assertNull(Bmi.value(0.0, 180))
        assertNull(Bmi.value(80.0, 30))
        assertNull(Bmi.value(-5.0, 180))
    }
}
