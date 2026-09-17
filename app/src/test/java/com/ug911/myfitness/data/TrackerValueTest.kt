package com.ug911.myfitness.data

import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.numeric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerValueTest {

    @Test
    fun `values survive a round trip through storage`() {
        val cases = listOf(
            TrackerType.BOOLEAN to TrackerValue.Flag(true),
            TrackerType.NUMBER to TrackerValue.Number(74.2),
            TrackerType.DURATION to TrackerValue.Duration(45),
            TrackerType.RATING to TrackerValue.Rating(4),
            TrackerType.TEXT to TrackerValue.Text("ate late again"),
            TrackerType.SELECT to TrackerValue.Choice("3+"),
        )

        cases.forEach { (type, value) ->
            assertEquals(value, TrackerValue.decode(type, value.encode()))
        }
    }

    @Test
    fun `an unchecked boolean decodes as false rather than missing`() {
        assertEquals(TrackerValue.Flag(false), TrackerValue.decode(TrackerType.BOOLEAN, "0"))
        assertEquals(TrackerValue.Flag(true), TrackerValue.decode(TrackerType.BOOLEAN, "true"))
    }

    @Test
    fun `corrupt or blank stored values decode to nothing instead of throwing`() {
        assertNull(TrackerValue.decode(TrackerType.NUMBER, "not-a-number"))
        assertNull(TrackerValue.decode(TrackerType.RATING, ""))
        assertNull(TrackerValue.decode(TrackerType.TEXT, "   "))
        assertNull(TrackerValue.decode(TrackerType.BOOLEAN, null))
    }

    @Test
    fun `only numeric values report a number`() {
        assertEquals(1.0, TrackerValue.Flag(true).numeric()!!, 0.001)
        assertEquals(0.0, TrackerValue.Flag(false).numeric()!!, 0.001)
        assertEquals(45.0, TrackerValue.Duration(45).numeric()!!, 0.001)
        assertNull(TrackerValue.Text("hello").numeric())
        assertNull(TrackerValue.Choice("2").numeric())
    }
}
