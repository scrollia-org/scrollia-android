package org.scrollia.app.helpers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The search field and the state of its current [query]: the trimmed, non-blank [text].
 * Results, loading and failure always belong to [query]; an empty query means no search.
 */
data class SearchState<T>(
    val text: String = "",
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val failure: LoadFailure? = null,
    val completed: Boolean = false
) {
    val isActive: Boolean get() = query.isNotEmpty()

    val content: ContentState
        get() = when {
            results.isNotEmpty() -> ContentState.RESULTS
            failure != null -> failure.contentState
            completed -> ContentState.EMPTY
            else -> ContentState.LOADING
        }
}

/**
 * Debounced search. A new query cancels the previous one, and an answer is applied only while
 * its query is still the current one, so a late response can never overwrite newer input.
 */
class SearchController<T>(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 500L,
    private val search: suspend (query: String) -> Result<List<T>>
) {
    private val _state = MutableStateFlow(SearchState<T>())
    val state: StateFlow<SearchState<T>> = _state.asStateFlow()

    private var job: Job? = null

    fun open() {
        _state.value = _state.value.copy(isVisible = true)
    }

    /** Close the field: the text and any pending search go too. */
    fun close() {
        job?.cancel()
        _state.value = SearchState()
    }

    fun onTextChange(text: String) {
        val query = text.trim()
        val current = _state.value
        if (query == current.query) {
            _state.value = current.copy(text = text)
            return
        }
        job?.cancel()
        if (query.isEmpty()) {
            _state.value = SearchState(text = text, isVisible = current.isVisible)
            return
        }
        _state.value = SearchState(
            text = text, isVisible = current.isVisible, query = query, isLoading = true
        )
        job = scope.launch {
            delay(debounceMillis)
            run(query)
        }
    }

    /** Repeat the current query after a failure. Safe to call repeatedly. */
    fun retry() {
        val current = _state.value
        if (!current.isActive || current.isLoading || current.failure == null) return
        _state.value = current.copy(isLoading = true, failure = null)
        val query = current.query
        job = scope.launch { run(query) }
    }

    /** Connectivity came back: repeat the query only if it failed for lack of it. */
    fun retryAfterReconnect() {
        if (_state.value.failure == LoadFailure.OFFLINE) retry()
    }

    private suspend fun run(query: String) {
        val result = try {
            search(query)
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            Result.failure(exc)
        }
        val current = _state.value
        if (current.query != query) return
        _state.value = current.copy(
            results = result.getOrDefault(emptyList()),
            isLoading = false,
            failure = result.exceptionOrNull()?.let(LoadFailure::of),
            completed = result.isSuccess
        )
    }
}
