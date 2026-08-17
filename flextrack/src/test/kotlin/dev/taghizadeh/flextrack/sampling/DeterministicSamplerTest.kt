package dev.taghizadeh.flextrack.sampling

import dev.taghizadeh.flextrack.event.TestEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeterministicSamplerTest {
    @Test
    fun `matches shared Unicode FNV vector`() {
        assertEquals(538_106_393L, DeterministicSampler.stableHash("नमस्ते"))
        assertTrue(DeterministicSampler.shouldSample("नमस्ते", 0.25))
    }

    @Test
    fun `uses user then session then event name as identity`() {
        assertEquals(
            "user-1",
            DeterministicSampler.samplingKey(
                TestEvent(userId = "user-1", sessionId = "session-1"),
            ),
        )
        assertEquals(
            "session-1",
            DeterministicSampler.samplingKey(TestEvent(userId = "", sessionId = "session-1")),
        )
        assertEquals("purchase", DeterministicSampler.samplingKey(TestEvent()))
    }

    @Test
    fun `handles boundaries and essential bypass`() {
        assertFalse(DeterministicSampler.shouldSample("value", 0.0))
        assertTrue(DeterministicSampler.shouldSample("value", 1.0))
        assertTrue(DeterministicSampler.shouldSample(TestEvent(isEssential = true), 0.0))
    }
}
