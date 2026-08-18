package dev.flextrack.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.flextrack.sample.ui.DeliveryUiState
import dev.flextrack.sample.ui.DeliveryViewModel
import dev.flextrack.sample.ui.EnrichmentScreen
import dev.flextrack.sample.ui.EcommerceScreen
import dev.flextrack.sample.ui.SampleHomeScreen
import dev.flextrack.sample.ui.SampleViewModel
import dev.flextrack.sample.ui.SettingsScreen
import dev.flextrack.sample.ui.UserJourneyScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlexTrackSampleApp() }
    }
}

@Composable
fun FlexTrackSampleApp() {
    MaterialTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                val viewModel: SampleViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                SampleHomeScreen(state, viewModel::track, navController::navigate)
            }
            composable("ecommerce") {
                val viewModel: SampleViewModel = hiltViewModel()
                EcommerceScreen(navController::popBackStack, viewModel::track)
            }
            composable("journey") {
                val viewModel: SampleViewModel = hiltViewModel()
                UserJourneyScreen(navController::popBackStack, viewModel::track)
            }
            composable("settings") {
                val sampleViewModel: SampleViewModel = hiltViewModel()
                val deliveryViewModel: DeliveryViewModel = hiltViewModel()
                val state by deliveryViewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state,
                    navController::popBackStack,
                    deliveryViewModel::setConsent,
                    deliveryViewModel::setOnline,
                    deliveryViewModel::flush,
                    sampleViewModel::track,
                )
            }
            composable("enrichment") {
                val viewModel: SampleViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                EnrichmentScreen(
                    state,
                    navController::popBackStack,
                    viewModel::toggleTransformer,
                    viewModel::track,
                    viewModel::clearLog,
                )
            }
            composable("delivery") {
                val viewModel: DeliveryViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                DeliveryScreen(
                    state = state,
                    onBack = navController::popBackStack,
                    onOnlineChanged = viewModel::setOnline,
                    onConsentChanged = viewModel::setConsent,
                    onRetryHealthyChanged = viewModel::setRetryDestinationHealthy,
                    onTrack = viewModel::track,
                    onFlush = viewModel::flush,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpenDeliveryLab: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("FlexTrack Kotlin") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Production-style sample", style = MaterialTheme.typography.headlineMedium)
            Text("Jetpack Compose · MVVM · Hilt · StateFlow · DataStore")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline delivery", style = MaterialTheme.typography.titleLarge)
                    Text("Persist events, restore after process death, and retry only failed destinations.")
                    Button(onClick = onOpenDeliveryLab) { Text("Open Delivery Lab") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryScreen(
    state: DeliveryUiState,
    onBack: () -> Unit,
    onOnlineChanged: (Boolean) -> Unit,
    onConsentChanged: (Boolean) -> Unit,
    onRetryHealthyChanged: (Boolean) -> Unit,
    onTrack: () -> Unit,
    onFlush: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Offline Delivery Lab") },
            navigationIcon = { OutlinedButton(onClick = onBack) { Text("Back") } },
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow("Network available", state.isOnline, onOnlineChanged, "network-toggle")
            SettingRow("Analytics consent", state.hasConsent, onConsentChanged, "consent-toggle")
            SettingRow(
                "Retry destination healthy",
                state.retryDestinationHealthy,
                onRetryHealthyChanged,
                "failure-toggle",
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Pending events: ${state.queueSize}",
                        modifier = Modifier.testTag("queue-count"),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Reliable: ${state.reliableDeliveries} delivered / ${state.reliableAttempts} attempts")
                    Text("Retry: ${state.retryDeliveries} delivered / ${state.retryAttempts} attempts")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onTrack,
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f).testTag("track-event"),
                ) { Text("Track event") }
                OutlinedButton(
                    onClick = onFlush,
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f).testTag("flush-queue"),
                ) { Text("Flush queue") }
            }

            ResultCard("Delivered", state.deliveredIds)
            ResultCard("Failed", state.failedIds)
            ResultCard("Queued", state.queuedIds)
            state.flushResult?.let {
                Text("Flush: ${it.attemptedEvents} attempted · ${it.deliveredEvents} delivered · ${it.remainingEvents} remaining")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun ResultCard(label: String, values: List<String>) {
    Text("$label: ${values.ifEmpty { listOf("none") }.joinToString()}")
}
