package dev.flextrack.sample

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.flextrack.runtime.FlushResult
import dev.flextrack.sample.ui.DeliveryUiState
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class DeliveryScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun rendersOfflineQueueAndSelectiveRetryResult() {
        setScreen(
            DeliveryUiState(
                loading = false,
                isOnline = false,
                queueSize = 2,
                deliveredIds = listOf("sample_reliable"),
                failedIds = listOf("sample_retry"),
                queuedIds = listOf("sample_retry"),
                flushResult = FlushResult(1, 0, 2),
            ),
        )

        compose.onNodeWithText("Offline Delivery Lab").assertIsDisplayed()
        compose.onNodeWithTag("queue-count").assertIsDisplayed()
        compose.onNodeWithText("Pending events: 2").assertIsDisplayed()
        compose.onNodeWithText("Delivered: sample_reliable").assertIsDisplayed()
        compose.onNodeWithText("Queued: sample_retry").assertIsDisplayed()
    }

    @Test
    fun controlsExposeNetworkConsentTrackAndFlushActions() {
        var online: Boolean? = null
        var consent: Boolean? = null
        var tracks = 0
        var flushes = 0
        setScreen(
            state = DeliveryUiState(
                loading = false,
                isOnline = false,
                hasConsent = false,
            ),
            onOnlineChanged = { online = it },
            onConsentChanged = { consent = it },
            onTrack = { tracks++ },
            onFlush = { flushes++ },
        )

        compose.onNodeWithTag("network-toggle").performClick()
        compose.onNodeWithTag("consent-toggle").performClick()
        compose.onNodeWithTag("track-event").assertIsEnabled().performClick()
        compose.onNodeWithTag("flush-queue").assertIsEnabled().performClick()

        assertEquals(true, online)
        assertEquals(true, consent)
        assertEquals(1, tracks)
        assertEquals(1, flushes)
    }

    @Test
    fun loadingPreventsDuplicateTrackAndFlushActions() {
        setScreen(DeliveryUiState(loading = true))

        compose.onNodeWithTag("track-event").assertIsNotEnabled()
        compose.onNodeWithTag("flush-queue").assertIsNotEnabled()
    }

    private fun setScreen(
        state: DeliveryUiState,
        onOnlineChanged: (Boolean) -> Unit = {},
        onConsentChanged: (Boolean) -> Unit = {},
        onTrack: () -> Unit = {},
        onFlush: () -> Unit = {},
    ) {
        compose.activity.setContent {
            DeliveryScreen(
                state = state,
                onBack = {},
                onOnlineChanged = onOnlineChanged,
                onConsentChanged = onConsentChanged,
                onRetryHealthyChanged = {},
                onTrack = onTrack,
                onFlush = onFlush,
            )
        }
        compose.waitForIdle()
    }
}
