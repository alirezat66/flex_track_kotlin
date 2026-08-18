package dev.flextrack.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.flextrack.runtime.DispatchResult
import dev.flextrack.runtime.FlushResult
import dev.flextrack.sample.data.DeliveryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeliveryUiState(
    val loading: Boolean = true,
    val isOnline: Boolean = true,
    val hasConsent: Boolean = true,
    val retryDestinationHealthy: Boolean = true,
    val queueSize: Int = 0,
    val reliableAttempts: Int = 0,
    val reliableDeliveries: Int = 0,
    val retryAttempts: Int = 0,
    val retryDeliveries: Int = 0,
    val deliveredIds: List<String> = emptyList(),
    val failedIds: List<String> = emptyList(),
    val queuedIds: List<String> = emptyList(),
    val flushResult: FlushResult? = null,
    val error: String? = null,
)

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val repository: DeliveryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DeliveryUiState())
    val state: StateFlow<DeliveryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
            combine(repository.isOnline, repository.hasConsent, ::Pair)
                .collect { (online, consent) ->
                    refresh { copy(isOnline = online, hasConsent = consent, loading = false) }
                }
        }
    }

    fun setOnline(value: Boolean) = runAction { repository.setOnline(value) }
    fun setConsent(value: Boolean) = runAction { repository.setConsent(value) }

    fun setRetryDestinationHealthy(value: Boolean) {
        repository.setRetryDestinationHealthy(value)
        _state.update { it.copy(retryDestinationHealthy = value) }
    }

    fun track() = runAction {
        val result = repository.track()
        refresh { withDispatch(result) }
    }

    fun flush() = runAction {
        val result = repository.flush()
        refresh { copy(flushResult = result) }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.message ?: failure.toString()) }
                }
            refresh { copy(loading = false) }
        }
    }

    private suspend fun refresh(change: DeliveryUiState.() -> DeliveryUiState) {
        _state.update {
            it.change().copy(
                queueSize = repository.queueSize(),
                reliableAttempts = repository.reliableTracker.attempts,
                reliableDeliveries = repository.reliableTracker.deliveries,
                retryAttempts = repository.retryTracker.attempts,
                retryDeliveries = repository.retryTracker.deliveries,
                retryDestinationHealthy = repository.retryDestinationHealthy(),
            )
        }
    }

    private fun DeliveryUiState.withDispatch(result: DispatchResult): DeliveryUiState = copy(
        deliveredIds = result.successfulTrackerIds,
        failedIds = result.failures.map { it.trackerId },
        queuedIds = result.queuedTrackerIds,
        flushResult = null,
    )
}
