package com.anos.myscreenoff.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonGeometryTest {

    @Test
    fun positionSpansTheAreaBetweenTheMargins() {
        assertEquals(10, ButtonGeometry.position(0f, areaPx = 1000, sizePx = 100, marginPx = 10))
        assertEquals(890, ButtonGeometry.position(1f, areaPx = 1000, sizePx = 100, marginPx = 10))
        assertEquals(450, ButtonGeometry.position(0.5f, areaPx = 1000, sizePx = 100, marginPx = 10))
    }

    @Test
    fun fractionIsTheInverseOfPosition() {
        val position = ButtonGeometry.position(0.25f, areaPx = 1000, sizePx = 100, marginPx = 10)
        assertEquals(0.25f, ButtonGeometry.fraction(position, areaPx = 1000, sizePx = 100, marginPx = 10), 0.001f)
    }

    @Test
    fun clampKeepsTheButtonInsideTheArea() {
        assertEquals(10, ButtonGeometry.clamp(-50, areaPx = 1000, sizePx = 100, marginPx = 10))
        assertEquals(890, ButtonGeometry.clamp(2000, areaPx = 1000, sizePx = 100, marginPx = 10))
        assertEquals(300, ButtonGeometry.clamp(300, areaPx = 1000, sizePx = 100, marginPx = 10))
    }

    @Test
    fun buttonLargerThanTheAreaStaysAtTheMargin() {
        assertEquals(10, ButtonGeometry.position(1f, areaPx = 50, sizePx = 100, marginPx = 10))
        assertEquals(0f, ButtonGeometry.fraction(10, areaPx = 50, sizePx = 100, marginPx = 10), 0f)
    }

    @Test
    fun snapPicksTheNearerEdge() {
        assertEquals(0f, ButtonGeometry.snap(0.49f), 0f)
        assertEquals(1f, ButtonGeometry.snap(0.5f), 0f)
    }

    @Test
    fun isWithinUsesDistanceFromTheTargetCenter() {
        assertTrue(ButtonGeometry.isWithin(103f, 104f, targetX = 100f, targetY = 100f, radius = 5f))
        assertFalse(ButtonGeometry.isWithin(104f, 104f, targetX = 100f, targetY = 100f, radius = 5f))
    }
}
