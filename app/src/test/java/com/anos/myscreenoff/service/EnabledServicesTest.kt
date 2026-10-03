package com.anos.myscreenoff.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledServicesTest {

    private val self = "com.anos.myscreenoff/com.anos.myscreenoff.service.ScreenOffService"
    private val other = "com.google.android.marvin.talkback/.TalkBackService"

    @Test
    fun containsMatchesShortAndFullForms() {
        assertTrue(EnabledServices.contains("$other:$self", self))
        assertTrue(EnabledServices.contains("com.anos.myscreenoff/.service.ScreenOffService", self))
        assertFalse(EnabledServices.contains(other, self))
    }

    @Test
    fun containsHandlesAnEmptyOrMissingList() {
        assertFalse(EnabledServices.contains(null, self))
        assertFalse(EnabledServices.contains("", self))
    }

    @Test
    fun enablingAddsOnceAndKeepsOtherServices() {
        assertEquals("$other:$self", EnabledServices.with(other, self, enabled = true))
        assertEquals("$other:$self", EnabledServices.with("$other:$self", self, enabled = true))
        assertEquals(self, EnabledServices.with(null, self, enabled = true))
    }

    @Test
    fun disablingRemovesEveryFormAndKeepsOtherServices() {
        assertEquals(other, EnabledServices.with("$self:$other", self, enabled = false))
        assertEquals(other, EnabledServices.with("com.anos.myscreenoff/.service.ScreenOffService:$other", self, enabled = false))
        assertEquals("", EnabledServices.with(self, self, enabled = false))
    }
}
