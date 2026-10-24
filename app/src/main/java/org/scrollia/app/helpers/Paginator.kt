/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scrollia.app.helpers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One page from the server, already filtered; [hasNext] comes from the API (`next != null`). */
data class PageResult<T>(val items: List<T>, val hasNext: Boolean)

/**
 * A paged list for one input (query, filters or category). [generation] changes on every
 * [Paginator.reset], so a response for an older input can never land in this one.
 */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    val nextPage: Long = 1L,
    val endReached: Boolean = false,
    val isLoading: Boolean = false,
    val failure: LoadFailure? = null,
    val generation: Int = 0
) {
    val content: ContentState
        get() = when {
            items.isNotEmpty() -> ContentState.RESULTS
            failure != null -> failure.contentState
            endReached -> ContentState.EMPTY
            else -> ContentState.LOADING
        }

    /** A later page is loading below the items. */
    val isAppending: Boolean get() = items.isNotEmpty() && isLoading

    /** A later page failed; the items stay and the footer offers Retry. */
    val appendFailure: LoadFailure? get() = failure.takeIf { items.isNotEmpty() }
}

/**
 * Loads pages one at a time. A failure keeps the items and the failed page: only [retry]
 * requests it again, so scroll callbacks cannot hammer a failing server. Pages are merged by
 * [idOf] (never duplicated) and ordered by [arrange].
 */
class Paginator<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> Any,
    private val arrange: (List<T>) -> List<T> = { it },
    private val request: suspend (page: Long) -> Result<PageResult<T>>
) {
    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private var job: Job? = null

    /** Load the next page, unless one is in flight, the end was reached, or the last one failed. */
    fun loadNext() {
        val current = _state.value
        if (current.isLoading || current.endReached || current.failure != null) return
        launchPage(current)
    }

    /** Request the failed page again. Safe to call repeatedly. */
    fun retry() {
        val current = _state.value
        if (current.isLoading || current.failure == null) return
        launchPage(current)
    }

    /**
     * Connectivity came back: retry a page that failed for lack of it, or any failed later page.
     * Nothing happens otherwise, so a stream of network callbacks issues no requests.
     */
    fun retryAfterReconnect() {
        val current = _state.value
        if (current.failure == LoadFailure.OFFLINE || current.appendFailure != null) retry()
    }

    /** Start over for a new input: the request in flight is cancelled and its answer ignored. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = PagedState(generation = _state.value.generation + 1)
    }

    private fun launchPage(from: PagedState<T>) {
        val generation = from.generation
        val page = from.nextPage
        _state.value = from.copy(isLoading = true, failure = null)
        job = scope.launch {
            val result = try {
                request(page)
            } catch (exc: CancellationException) {
                throw exc
            } catch (exc: Exception) {
                Result.failure(exc)
            }
            val current = _state.value
            if (current.generation != generation) return@launch
            result.fold(
                onSuccess = { pageResult ->
                    val merged = arrange((current.items + pageResult.items).distinctBy(idOf))
                    val next = current.copy(
                        items = merged,
                        nextPage = page + 1,
                        endReached = !pageResult.hasNext,
                        isLoading = false,
                        failure = null
                    )
                    _state.value = next
                    // A page that adds nothing visible (all filtered out or duplicates) would
                    // leave the list waiting for a scroll that cannot happen.
                    if (merged.size == current.items.size && !next.endReached) launchPage(next)
                },
                onFailure = { error ->
                    _state.value = current.copy(isLoading = false, failure = LoadFailure.of(error))
                }
            )
        }
    }
}
