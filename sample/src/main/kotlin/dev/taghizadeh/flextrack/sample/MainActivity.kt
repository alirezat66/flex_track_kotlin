package dev.taghizadeh.flextrack.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.taghizadeh.flextrack.event.FlexEvent
import dev.taghizadeh.flextrack.routing.ConsentState
import dev.taghizadeh.flextrack.routing.RoutingConfiguration
import dev.taghizadeh.flextrack.routing.RoutingEngine
import dev.taghizadeh.flextrack.routing.RoutingRule
import dev.taghizadeh.flextrack.routing.TrackerGroup
import dev.taghizadeh.flextrack.runtime.FileEventQueue
import dev.taghizadeh.flextrack.runtime.FlexTrackClient
import dev.taghizadeh.flextrack.runtime.Tracker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val scope = MainScope()
    private lateinit var output: TextView
    private lateinit var client: FlexTrackClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { text = getString(R.string.ready) }
        val track = Button(this).apply {
            text = getString(R.string.track_purchase)
            setOnClickListener { sendPurchase() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(track)
            addView(output)
        })

        client = FlexTrackClient(
            routingEngine = RoutingEngine(
                RoutingConfiguration(
                    rules = listOf(
                        RoutingRule(
                            targetGroup = TrackerGroup("analytics", listOf("console")),
                        ),
                    ),
                ),
            ),
            queue = FileEventQueue(this),
            consentProvider = { ConsentState(general = true) },
        )
        scope.launch {
            client.register(ConsoleTracker { output.text = it })
            client.start()
            client.flush()
        }
    }

    private fun sendPurchase() {
        scope.launch {
            val result = client.track(PurchaseEvent())
            output.text = getString(
                R.string.result,
                result.successfulTrackerIds.joinToString(),
                result.queuedTrackerIds.joinToString(),
            )
        }
    }

    override fun onDestroy() {
        scope.launch { client.shutdown() }.invokeOnCompletion { scope.cancel() }
        super.onDestroy()
    }
}

private class PurchaseEvent : FlexEvent() {
    override val name: String = "purchase"
    override val properties: Map<String, Any?> = mapOf("plan" to "pro", "currency" to "EUR")
}

private class ConsoleTracker(private val log: (String) -> Unit) : Tracker {
    override val id: String = "console"

    override suspend fun track(event: FlexEvent) {
        log("${event.name}: ${event.properties}")
    }
}
