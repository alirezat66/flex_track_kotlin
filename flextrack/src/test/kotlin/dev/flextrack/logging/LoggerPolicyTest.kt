package dev.flextrack.logging

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggerPolicyTest {
    @Test
    fun `release builds never enable Logcat at any level`() {
        FlexTrackLogLevel.entries.forEach { level ->
            assertFalse(shouldEnableLogcat(isDebuggable = false, level))
        }
    }

    @Test
    fun `debug builds honor off basic and verbose levels`() {
        assertFalse(shouldEnableLogcat(true, FlexTrackLogLevel.OFF))
        assertTrue(shouldEnableLogcat(true, FlexTrackLogLevel.BASIC))
        assertTrue(shouldEnableLogcat(true, FlexTrackLogLevel.VERBOSE))
    }
}
