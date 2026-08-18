package dev.flextrack.runtime

import android.content.Context
import android.util.AtomicFile
import dev.flextrack.event.FlexEvent
import dev.flextrack.routing.EventCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Durable JSON queue stored in the application's private files directory. */
public class FileEventQueue(
    context: Context,
    fileName: String = "flextrack-event-queue.json",
) : EventQueue {
    private val file: File = File(context.applicationContext.filesDir, fileName)
    private val atomicFile: AtomicFile = AtomicFile(file)
    private val mutex: Mutex = Mutex()

    init {
        require(fileName.isNotBlank() && !fileName.contains(File.separatorChar)) {
            "fileName must be a simple non-blank file name"
        }
    }

    override suspend fun enqueue(item: QueuedEvent): Unit = mutate { items ->
        if (items.none { it.id == item.id }) items += item
    }

    override suspend fun read(limit: Int): List<QueuedEvent> {
        require(limit > 0) { "limit must be positive" }
        return mutex.withLock {
            withContext(Dispatchers.IO) { immutableSnapshot(load().take(limit)) }
        }
    }

    override suspend fun replace(item: QueuedEvent): Unit = mutate { items ->
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item
    }

    override suspend fun remove(id: String): Unit = mutate { items ->
        items.removeAll { it.id == id }
    }

    override suspend fun size(): Int = mutex.withLock {
        withContext(Dispatchers.IO) { load().size }
    }

    private suspend fun mutate(block: (MutableList<QueuedEvent>) -> Unit) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val items = load()
                block(items)
                persist(items)
            }
        }
    }

    private fun load(): MutableList<QueuedEvent> {
        if (!file.exists() || file.length() == 0L) return mutableListOf()
        val array = JSONArray(atomicFile.readFully().toString(Charsets.UTF_8))
        return MutableList(array.length()) { index -> array.getJSONObject(index).toQueuedEvent() }
    }

    private fun persist(items: List<QueuedEvent>) {
        file.parentFile?.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(JSONArray(items.map(QueuedEvent::toJson)).toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }
}

private fun QueuedEvent.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("trackerIds", JSONArray(trackerIds))
    put("attempts", attempts)
    put("queuedAt", queuedAt.toString())
    put("event", event.toJson())
}

private fun FlexEvent.toJson(): JSONObject = JSONObject().apply {
    put("eventId", eventId)
    put("timestamp", timestamp.toString())
    put("name", name)
    put("properties", properties?.let(::JSONObject) ?: JSONObject.NULL)
    put("category", category?.name ?: JSONObject.NULL)
    put("containsPII", containsPII)
    put("requiresConsent", requiresConsent)
    put("isHighVolume", isHighVolume)
    put("isEssential", isEssential)
    put("userId", userId ?: JSONObject.NULL)
    put("sessionId", sessionId ?: JSONObject.NULL)
}

private fun JSONObject.toQueuedEvent(): QueuedEvent {
    val trackerArray = getJSONArray("trackerIds")
    return QueuedEvent(
        id = getString("id"),
        event = getJSONObject("event").toEvent(),
        trackerIds = List(trackerArray.length()) { trackerArray.getString(it) },
        attempts = getInt("attempts"),
        queuedAt = Instant.parse(getString("queuedAt")),
    )
}

private fun JSONObject.toEvent(): FlexEvent {
    val source = this
    return object : FlexEvent(
        eventId = source.getString("eventId"),
        timestamp = Instant.parse(source.getString("timestamp")),
    ) {
        override val name: String = source.getString("name")
        override val properties: Map<String, Any?>? = source.optJSONObject("properties")?.toMap()
        override val category: EventCategory? = source.nullableString("category")?.let(::EventCategory)
        override val containsPII: Boolean = source.getBoolean("containsPII")
        override val requiresConsent: Boolean = source.getBoolean("requiresConsent")
        override val isHighVolume: Boolean = source.getBoolean("isHighVolume")
        override val isEssential: Boolean = source.getBoolean("isEssential")
        override val userId: String? = source.nullableString("userId")
        override val sessionId: String? = source.nullableString("sessionId")
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> value
    }
}

private fun JSONArray.toList(): List<Any?> = List(length()) { index ->
    when (val value = get(index)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> value.toList()
        else -> value
    }
}
