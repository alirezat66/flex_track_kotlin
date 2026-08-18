package dev.flextrack.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.flextrack.sample.data.DeliveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SampleUiState(
    val message: String = "Ready",
    val eventLog: List<String> = emptyList(),
    val transformerEnabled: Boolean = false,
)

@HiltViewModel
class SampleViewModel @Inject constructor(
    private val repository: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SampleUiState())
    val state: StateFlow<SampleUiState> = _state.asStateFlow()

    fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        viewModelScope.launch {
            val enriched = if (_state.value.transformerEnabled) {
                properties + mapOf("app_version" to "1.0", "environment" to "sample")
            } else properties
            runCatching { repository.trackSample(name, enriched) }
                .onSuccess { result ->
                    val destination = result.successfulTrackerIds.ifEmpty { result.queuedTrackerIds }
                    val line = "$name → ${destination.joinToString().ifEmpty { "not routed" }}"
                    _state.update {
                        it.copy(message = line, eventLog = (listOf(line) + it.eventLog).take(20))
                    }
                }
                .onFailure { failure -> _state.update { it.copy(message = failure.message ?: "Failed") } }
        }
    }

    fun toggleTransformer() {
        _state.update { it.copy(transformerEnabled = !it.transformerEnabled) }
    }

    fun clearLog() = _state.update { it.copy(eventLog = emptyList()) }
}
