package dev.flextrack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlexTrackTest {
    @Test
    fun `package metadata identifies SDK and contract versions`() {
        assertEquals("1.1.0", FlexTrack.VERSION)
        assertEquals("1.0.0", FlexTrack.CORE_SPEC_VERSION)
    }
}
