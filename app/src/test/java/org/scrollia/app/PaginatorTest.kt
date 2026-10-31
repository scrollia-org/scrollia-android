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

package org.scrollia.app


import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.scrollia.app.api.HttpStatusException
import org.scrollia.app.api.MalformedResponseException
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.helpers.LoadFailure
import org.scrollia.app.helpers.PageResult
import org.scrollia.app.helpers.Paginator

class PaginatorTest {

    private data class Book(val id: Int, val title: String = "Book $id")

    /** Answers each page request with whatever the test completes it with. */
    private class FakeServer {
        val requests = mutableListOf<Long>()
        private val pending = mutableListOf<CompletableDeferred<Result<PageResult<Book>>>>()

        suspend fun request(page: Long): Result<PageResult<Book>> {
            requests += page
            val answer = CompletableDeferred<Result<PageResult<Book>>>()
            pending += answer
            // Like an HTTP call that has already left: cancellation does not stop the answer.
            return withContext(NonCancellable) { answer.await() }
        }

        fun respond(vararg ids: Int, hasNext: Boolean = true) {
            pending.removeAt(0).complete(
                Result.success(PageResult(ids.map { Book(it) }, hasNext))
            )
        }

        fun fail(error: Throwable) {
            pending.removeAt(0).complete(Result.failure(error))
        }

        val inFlight: Int get() = pending.size
    }

    /** Run everything due, past any debounce (advanceUntilIdle skips background work). */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.paginator(
        server: FakeServer,
        arrange: (List<Book>) -> List<Book> = { it }
    ) = Paginator(scope = backgroundScope, idOf = Book::id, arrange = arrange, request = server::request)

    @Test
    fun `test paginator with valid result`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)

        paginator.loadNext()
        settle()
        assertThat(paginator.state.value.content).isEqualTo(ContentState.LOADING)
        server.respond(1, 2, 3)
        settle()

        val state = paginator.state.value
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.items.map { it.id }).containsExactly(1, 2, 3).inOrder()
        assertThat(state.nextPage).isEqualTo(2)
        assertThat(state.isLoading).isFalse()
        assertThat(state.failure).isNull()
    }

    @Test
    fun `test paginator with error result`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)

        paginator.loadNext()
        settle()
        server.fail(NullPointerException("meow"))
        settle()

        val state = paginator.state.value
        assertThat(state.content).isEqualTo(ContentState.ERROR)
        assertThat(state.nextPage).isEqualTo(1)
        assertThat(state.items).isEmpty()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `test paginator reset`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1)
        settle()

        paginator.reset()

        val state = paginator.state.value
        assertThat(state.items).isEmpty()
        assertThat(state.nextPage).isEqualTo(1)
        assertThat(state.content).isEqualTo(ContentState.LOADING)
        assertThat(state.generation).isEqualTo(1)
    }

    @Test
    fun `successful empty complete result is Empty`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(hasNext = false)
        settle()

        assertThat(paginator.state.value.content).isEqualTo(ContentState.EMPTY)
    }

    @Test
    fun `network failure is Offline and server failures are Error, never Empty`() = runTest {
        val cases = mapOf(
            UnknownHostException("api.scrollia.org") to ContentState.OFFLINE,
            IOException("timeout") to ContentState.OFFLINE,
            HttpStatusException(500) to ContentState.ERROR,
            MalformedResponseException(IllegalArgumentException()) to ContentState.ERROR
        )
        for ((error, expected) in cases) {
            val server = FakeServer()
            val paginator = paginator(server)
            paginator.loadNext()
            settle()
            server.fail(error)
            settle()
            assertThat(paginator.state.value.content).isEqualTo(expected)
        }
    }

    @Test
    fun `retry requests the same page and a success leaves no sticky error`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.fail(IOException())
        settle()

        paginator.retry()
        paginator.retry()
        settle()
        assertThat(server.requests).containsExactly(1L, 1L).inOrder()
        assertThat(paginator.state.value.content).isEqualTo(ContentState.LOADING)
        server.respond(1, 2)
        settle()

        val state = paginator.state.value
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.failure).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `scroll callbacks never duplicate an in-flight request`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        repeat(5) { paginator.loadNext() }
        settle()

        assertThat(server.requests).containsExactly(1L)
        assertThat(server.inFlight).isEqualTo(1)
    }

    @Test
    fun `append failure keeps items and the failed page until retry`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1, 2)
        settle()
        paginator.loadNext()
        settle()
        server.fail(IOException())
        settle()

        var state = paginator.state.value
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.items.map { it.id }).containsExactly(1, 2)
        assertThat(state.appendFailure).isEqualTo(LoadFailure.OFFLINE)
        assertThat(state.nextPage).isEqualTo(2)

        // Scrolling does not retry by itself.
        paginator.loadNext()
        settle()
        assertThat(server.requests).containsExactly(1L, 2L).inOrder()

        paginator.retry()
        settle()
        server.respond(3, 4)
        settle()

        state = paginator.state.value
        assertThat(server.requests).containsExactly(1L, 2L, 2L).inOrder()
        assertThat(state.items.map { it.id }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(state.appendFailure).isNull()
        assertThat(state.isAppending).isFalse()
        assertThat(state.nextPage).isEqualTo(3)
    }

    @Test
    fun `reconnect retries only an eligible failure and never duplicates`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)

        // Nothing failed: no request.
        paginator.retryAfterReconnect()
        settle()
        assertThat(server.requests).isEmpty()

        paginator.loadNext()
        settle()
        // In flight: reconnect callbacks add nothing.
        repeat(3) { paginator.retryAfterReconnect() }
        settle()
        assertThat(server.requests).containsExactly(1L)

        server.fail(IOException())
        settle()
        repeat(3) { paginator.retryAfterReconnect() }
        settle()
        assertThat(server.requests).containsExactly(1L, 1L)
    }

    @Test
    fun `reconnect does not retry an initial server error`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.fail(HttpStatusException(500))
        settle()

        paginator.retryAfterReconnect()
        settle()

        assertThat(server.requests).containsExactly(1L)
        assertThat(paginator.state.value.content).isEqualTo(ContentState.ERROR)
    }

    @Test
    fun `reconnect retries a failed later page`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1)
        settle()
        paginator.loadNext()
        settle()
        server.fail(HttpStatusException(503))
        settle()

        paginator.retryAfterReconnect()
        settle()

        assertThat(server.requests).containsExactly(1L, 2L, 2L).inOrder()
    }

    @Test
    fun `a filtered-out page with a next link is not the end`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(hasNext = true)
        settle()

        var state = paginator.state.value
        assertThat(state.endReached).isFalse()
        assertThat(state.content).isEqualTo(ContentState.LOADING)
        assertThat(server.requests).containsExactly(1L, 2L).inOrder()

        server.respond(7, hasNext = false)
        settle()
        state = paginator.state.value
        assertThat(state.items.map { it.id }).containsExactly(7)
        assertThat(state.endReached).isTrue()
    }

    @Test
    fun `a null next link stops further requests`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1, 2, hasNext = false)
        settle()

        repeat(3) { paginator.loadNext() }
        paginator.retry()
        paginator.retryAfterReconnect()
        settle()

        val state = paginator.state.value
        assertThat(server.requests).containsExactly(1L)
        assertThat(state.endReached).isTrue()
        assertThat(state.isAppending).isFalse()
        assertThat(state.appendFailure).isNull()
    }

    @Test
    fun `responses from before a reset never land in the new list`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1, 2)
        settle()
        paginator.loadNext() // page 2 of the old filters, in flight
        settle()

        paginator.reset()
        paginator.loadNext() // page 1 of the new filters
        settle()

        server.respond(3, 4) // late answer for the old page 2
        settle()
        assertThat(paginator.state.value.items).isEmpty()
        assertThat(paginator.state.value.isLoading).isTrue()

        server.respond(10, 11)
        settle()
        val state = paginator.state.value
        assertThat(state.items.map { it.id }).containsExactly(10, 11).inOrder()
        assertThat(state.nextPage).isEqualTo(2)
    }

    @Test
    fun `a failure from before a reset is ignored too`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()

        paginator.reset()
        server.fail(IOException())
        settle()

        assertThat(paginator.state.value.failure).isNull()
        assertThat(paginator.state.value.isLoading).isFalse()
    }

    @Test
    fun `merged pages keep one entry per book and the selected order`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server, arrange = { books -> books.sortedByDescending { it.id } })
        paginator.loadNext()
        settle()
        server.respond(1, 5, 3)
        settle()
        paginator.loadNext()
        settle()
        server.respond(5, 4, 2)
        settle()

        assertThat(paginator.state.value.items.map { it.id })
            .containsExactly(5, 4, 3, 2, 1).inOrder()
    }

    @Test
    fun `a page of only duplicates continues to the next page`() = runTest {
        val server = FakeServer()
        val paginator = paginator(server)
        paginator.loadNext()
        settle()
        server.respond(1, 2)
        settle()
        paginator.loadNext()
        settle()
        server.respond(1, 2)
        settle()

        assertThat(server.requests).containsExactly(1L, 2L, 3L).inOrder()
        server.respond(3, hasNext = false)
        settle()
        assertThat(paginator.state.value.items.map { it.id }).containsExactly(1, 2, 3).inOrder()
    }
}
