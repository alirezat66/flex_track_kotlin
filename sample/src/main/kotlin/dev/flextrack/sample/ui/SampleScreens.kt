package dev.flextrack.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SampleDestination(val route: String, val title: String, val description: String)

val sampleDestinations = listOf(
    SampleDestination("ecommerce", "E-commerce", "Cart, purchase, and abandonment events"),
    SampleDestination("journey", "User journey", "Registration, profile, engagement, and churn"),
    SampleDestination("settings", "Settings", "Consent, runtime status, flush, and test tools"),
    SampleDestination("enrichment", "Enrichment", "Transform events and inspect the live log"),
    SampleDestination("delivery", "Delivery", "Persistent offline queue and selective retry"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleHomeScreen(
    state: SampleUiState,
    onTrack: (String, Map<String, Any?>) -> Unit,
    onNavigate: (String) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("FlexTrack control room") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TelemetryHeader("Android SDK sample", "Compose · MVVM · Hilt · StateFlow · DataStore")
                Text(state.message)
            }
            item {
                ActionGrid(
                    actions = listOf("Basic event", "Business event", "User event", "Error", "Performance", "Debug"),
                    onAction = { label ->
                        onTrack(label.lowercase().replace(' ', '_'), mapOf("surface" to "home"))
                    },
                )
            }
            items(sampleDestinations) { destination ->
                PageCard(destination.title, destination.description) { onNavigate(destination.route) }
            }
            item {
                PageCard("Batch events", "Send three events through the same runtime") {
                    repeat(3) { onTrack("batch_event", mapOf("index" to it)) }
                }
            }
        }
    }
}

data class Product(val id: String, val name: String, val price: Double)

@Composable
fun EcommerceScreen(onBack: () -> Unit, onTrack: (String, Map<String, Any?>) -> Unit) {
    val products = remember { listOf(Product("pro", "Pro plan", 29.0), Product("team", "Team plan", 79.0), Product("scale", "Scale plan", 149.0)) }
    val cart = remember { mutableStateListOf<Product>() }
    SamplePage("E-commerce", "Revenue events with a live cart", onBack) {
        item { Text("Cart: ${cart.size} items · €${cart.sumOf { it.price }}") }
        items(products) { product ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(product.name); Text("€${product.price}") }
                    Button(onClick = {
                        cart += product
                        onTrack("add_to_cart", mapOf("product_id" to product.id, "price" to product.price))
                    }) { Text("Add") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = cart.isNotEmpty(), onClick = {
                    onTrack("purchase", mapOf("items" to cart.size, "total" to cart.sumOf { it.price }))
                    cart.clear()
                }) { Text("Checkout") }
                OutlinedButton(enabled = cart.isNotEmpty(), onClick = {
                    onTrack("cart_abandonment", mapOf("items" to cart.size)); cart.clear()
                }) { Text("Abandon cart") }
                OutlinedButton(onClick = cart::clear) { Text("Clear") }
            }
        }
    }
}

@Composable
fun UserJourneyScreen(onBack: () -> Unit, onTrack: (String, Map<String, Any?>) -> Unit) {
    val stages = listOf("Welcome", "Registration", "Profile", "Features", "Engagement")
    var stage by remember { mutableIntStateOf(0) }
    SamplePage("User journey", "Stage ${stage + 1} of ${stages.size}", onBack) {
        item { TelemetryHeader(stages[stage], "Follow a complete lifecycle and inspect each emitted event.") }
        item {
            ActionGrid(
                actions = when (stage) {
                    1 -> listOf("Register email", "Register Google", "Register Apple")
                    2 -> listOf("Update profile", "Set user properties")
                    3 -> listOf("Search", "Favorites", "Share", "Notifications")
                    4 -> listOf("Deep engagement", "Churn risk")
                    else -> listOf("Get started")
                },
                onAction = { onTrack("journey_${it.lowercase().replace(' ', '_')}", mapOf("stage" to stage)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = stage > 0, onClick = { stage--; onTrack("journey_previous", mapOf("stage" to stage)) }) { Text("Previous") }
                Button(onClick = {
                    if (stage < stages.lastIndex) stage++ else stage = 0
                    onTrack("journey_next", mapOf("stage" to stage))
                }) { Text(if (stage == stages.lastIndex) "Complete" else "Next") }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: DeliveryUiState,
    onBack: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onOnline: (Boolean) -> Unit,
    onFlush: () -> Unit,
    onTrack: (String, Map<String, Any?>) -> Unit,
) {
    SamplePage("Settings", "Privacy, runtime status, and diagnostics", onBack) {
        item { ToggleCard("Analytics consent", state.hasConsent, onConsent) }
        item { ToggleCard("Network available", state.isOnline, onOnline) }
        item { TelemetryHeader("FlexTrack status", "${state.queueSize} queued · 2 registered destinations") }
        item {
            ActionGrid(listOf("Flush events", "Send test events", "Test routing", "Validate config", "Export log")) {
                when (it) {
                    "Flush events" -> onFlush()
                    else -> onTrack("settings_${it.lowercase().replace(' ', '_')}", emptyMap())
                }
            }
        }
    }
}

@Composable
fun EnrichmentScreen(
    state: SampleUiState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onTrack: (String, Map<String, Any?>) -> Unit,
    onClear: () -> Unit,
) {
    SamplePage("Event enrichment", "Attach shared context before routing", onBack) {
        item { ToggleCard("Context transformer", state.transformerEnabled) { onToggle() } }
        item {
            ActionGrid(listOf("Button event", "Page view", "Click wrapper")) {
                onTrack("enrichment_${it.lowercase().replace(' ', '_')}", mapOf("source" to "enrichment"))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Event log"); OutlinedButton(onClick = onClear) { Text("Clear") }
            }
        }
        if (state.eventLog.isEmpty()) item { Text("Fire an event to see it here.") }
        items(state.eventLog) { line -> Card(Modifier.fillMaxWidth()) { Text(line, Modifier.padding(12.dp)) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamplePage(title: String, subtitle: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text(title); Text(subtitle) } }, navigationIcon = { OutlinedButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TelemetryHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PageCard(title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title); Text(description) }; Button(onClick = onClick) { Text("Open") } } }
}

@Composable
private fun ActionGrid(actions: List<String>, onAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { actions.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { action -> OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f)) { Text(action) } } } } }
}

@Composable
private fun ToggleCard(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title); Switch(checked = value, onCheckedChange = onChange) } }
}
