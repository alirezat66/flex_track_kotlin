package dev.flextrack.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.flextrack.runtime.FlushResult
import dev.flextrack.sample.ui.DeliveryUiState
import org.junit.Rule
import org.junit.Test

class DeliveryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersOfflineQueueAndSelectiveRetryResult() {
        compose.setContent {
            DeliveryScreen(
                state = DeliveryUiState(
                    loading = false,
                    isOnline = false,
                    queueSize = 2,
                    deliveredIds = listOf("sample_reliable"),
                    failedIds = listOf("sample_retry"),
                    queuedIds = listOf("sample_retry"),
                    flushResult = FlushResult(1, 0, 2),
                ),
                onBack = {},
                onOnlineChanged = {},
                onConsentChanged = {},
                onRetryHealthyChanged = {},
                onTrack = {},
                onFlush = {},
            )
        }

        compose.onNodeWithText("Offline Delivery Lab").assertIsDisplayed()
        compose.onNodeWithTag("queue-count").assertIsDisplayed()
        compose.onNodeWithText("Pending events: 2").assertIsDisplayed()
        compose.onNodeWithText("Delivered: sample_reliable").assertIsDisplayed()
        compose.onNodeWithText("Queued: sample_retry").assertIsDisplayed()
    }
}
