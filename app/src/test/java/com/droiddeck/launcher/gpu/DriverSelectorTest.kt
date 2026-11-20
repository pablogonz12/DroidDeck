package com.droiddeck.launcher.gpu

import com.droiddeck.launcher.gpu.DriverSelector.Choice
import com.droiddeck.launcher.gpu.DriverSelector.Driver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DriverSelectorTest {
    private val open = Driver("Mali-open-r1", "Mali-open r1")
    private val open2 = Driver("Mali-open-r1-2", "Mali-open r1")
    private val panvk = Driver("PanVK-26", "PanVK 26")

    @Test fun defaultIsMaliBridge() = assertEquals(Choice.MALIBRIDGE, DriverSelector.active("", listOf(open, panvk)))
    @Test fun panvkSelectedIsNotMaliOpen() = assertEquals(Choice.MALIBRIDGE, DriverSelector.active("PanVK-26", listOf(open, panvk)))
    @Test fun maliOpenSelectedIsShown() = assertEquals(Choice.MALI_OPEN, DriverSelector.active("Mali-open-r1", listOf(open)))
    @Test fun stalePrefToRemovedDriverIsMaliBridge() = assertEquals(Choice.MALIBRIDGE, DriverSelector.active("Mali-open-r1", emptyList()))

    @Test fun selectingMaliOpenWithoutBundleSaysSo() {
        val p = DriverSelector.plan(Choice.MALI_OPEN, "", listOf(panvk))
        assertNull(p.linuxDriver); assertNotNull(p.error)
    }
    @Test fun selectingMaliOpenPicksNewestImport() = assertEquals("Mali-open-r1-2", DriverSelector.plan(Choice.MALI_OPEN, "", listOf(open, open2, panvk)).linuxDriver)
    @Test fun selectingMaliBridgeClearsOnlyMaliOpen() {
        assertEquals("", DriverSelector.plan(Choice.MALIBRIDGE, "Mali-open-r1", listOf(open)).linuxDriver)
        assertNull(DriverSelector.plan(Choice.MALIBRIDGE, "PanVK-26", listOf(open, panvk)).linuxDriver)
    }
    @Test fun parse() { assertEquals(Choice.MALI_OPEN, Choice.parse("mali-open")); assertNull(Choice.parse("panvk")) }
}
