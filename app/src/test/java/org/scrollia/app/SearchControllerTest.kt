package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.scrollia.app.api.HttpStatusException
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.helpers.LoadFailure
import org.scrollia.app.helpers.SearchController

class SearchControllerTest {

    /** Holds every search until the test answers it; late answers still arrive. */
    private class FakeSearch {
        val queries = mutableListOf<String>()
        private val pending = mutableMapOf<String, CompletableDeferred<Result<List<String>>>>()

        suspend fun search(query: String): Result<List<String>> {
            queries += query
            val answer = CompletableDeferred<Result<List<String>>>()
            pending[query] = answer
            return withContext(NonCancellable) { answer.await() }
        }

        fun respond(query: String, vararg titles: String) {
            pending.remove(query)!!.complete(Result.success(titles.toList()))
        }

        fun fail(query: String, error: Throwable) {
            pending.remove(query)!!.complete(Result.failure(error))
        }
    }

    /** Run everything due, past any debounce (advanceUntilIdle skips background work). */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.controller(search: FakeSearch) =
        SearchController(scope = backgroundScope, debounceMillis = 500L, search = search::search)

    @Test
    fun `typing is debounced into one search for the trimmed query`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.open()
        controller.onTextChange("du")
        advanceTimeBy(200)
        controller.onTextChange("dune ")
        advanceTimeBy(499)
        runCurrent()
        assertThat(search.queries).isEmpty()
        assertThat(controller.state.value.content).isEqualTo(ContentState.LOADING)

        advanceTimeBy(2)
        runCurrent()
        assertThat(search.queries).containsExactly("dune")
        val state = controller.state.value
        assertThat(state.text).isEqualTo("dune ")
        assertThat(state.query).isEqualTo("dune")
    }

    @Test
    fun `results and a successful empty search are distinct states`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        search.respond("dune", "Dune")
        settle()
        assertThat(controller.state.value.content).isEqualTo(ContentState.RESULTS)

        controller.onTextChange("zzzz")
        settle()
        search.respond("zzzz")
        settle()
        assertThat(controller.state.value.content).isEqualTo(ContentState.EMPTY)
    }

    @Test
    fun `a failed search is Offline or Error, never zero results`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        search.fail("dune", IOException("no route"))
        settle()
        assertThat(controller.state.value.content).isEqualTo(ContentState.OFFLINE)
        assertThat(controller.state.value.completed).isFalse()

        controller.onTextChange("emma")
        settle()
        search.fail("emma", HttpStatusException(500))
        settle()
        assertThat(controller.state.value.content).isEqualTo(ContentState.ERROR)
    }

    @Test
    fun `an old query's answer never overwrites the current query`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        controller.onTextChange("emma")
        settle()

        search.respond("dune", "Dune")
        settle()
        assertThat(controller.state.value.content).isEqualTo(ContentState.LOADING)

        search.fail("emma", IOException())
        settle()
        assertThat(controller.state.value.query).isEqualTo("emma")
        assertThat(controller.state.value.results).isEmpty()
        assertThat(controller.state.value.content).isEqualTo(ContentState.OFFLINE)
    }

    @Test
    fun `an old query's failure never overwrites the current results`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        controller.onTextChange("emma")
        settle()
        search.respond("emma", "Emma")
        settle()

        search.fail("dune", IOException())
        settle()

        val state = controller.state.value
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.results).containsExactly("Emma")
        assertThat(state.failure).isNull()
    }

    @Test
    fun `blank input cancels pending work and never searches`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.open()
        controller.onTextChange("dune")
        advanceTimeBy(300)
        controller.onTextChange("   ")
        settle()

        val state = controller.state.value
        assertThat(search.queries).isEmpty()
        assertThat(state.isActive).isFalse()
        assertThat(state.isVisible).isTrue()
        assertThat(state.text).isEqualTo("   ")
        assertThat(state.completed).isFalse()
    }

    @Test
    fun `retry repeats the same query and success clears the failure`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        search.fail("dune", IOException())
        settle()

        controller.retry()
        controller.retry()
        settle()
        assertThat(search.queries).containsExactly("dune", "dune")
        search.respond("dune", "Dune")
        settle()

        val state = controller.state.value
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.failure).isNull()
        assertThat(state.isLoading).isFalse()
        assertThat(state.query).isEqualTo("dune")
    }

    @Test
    fun `reconnect retries only an offline search, once`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("emma")
        settle()
        search.fail("emma", HttpStatusException(500))
        settle()
        controller.retryAfterReconnect()
        settle()
        assertThat(search.queries).containsExactly("emma")

        controller.onTextChange("dune")
        settle()
        search.fail("dune", IOException())
        settle()
        repeat(3) { controller.retryAfterReconnect() }
        settle()
        assertThat(search.queries).containsExactly("emma", "dune", "dune").inOrder()
        assertThat(controller.state.value.failure).isNull()
        assertThat(controller.state.value.isLoading).isTrue()
    }

    @Test
    fun `whitespace around the same query does not search again`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.onTextChange("dune")
        settle()
        search.respond("dune", "Dune")
        settle()

        controller.onTextChange(" dune ")
        settle()

        assertThat(search.queries).containsExactly("dune")
        assertThat(controller.state.value.results).containsExactly("Dune")
        assertThat(controller.state.value.text).isEqualTo(" dune ")
    }

    @Test
    fun `closing clears the text and ignores the pending answer`() = runTest {
        val search = FakeSearch()
        val controller = controller(search)
        controller.open()
        controller.onTextChange("dune")
        settle()

        controller.close()
        search.respond("dune", "Dune")
        settle()

        val state = controller.state.value
        assertThat(state.isVisible).isFalse()
        assertThat(state.text).isEmpty()
        assertThat(state.results).isEmpty()
    }

    @Test
    fun `transport errors are offline and everything else is an error`() {
        assertThat(LoadFailure.of(java.net.SocketTimeoutException())).isEqualTo(LoadFailure.OFFLINE)
        assertThat(LoadFailure.of(java.net.UnknownHostException())).isEqualTo(LoadFailure.OFFLINE)
        assertThat(LoadFailure.of(HttpStatusException(404))).isEqualTo(LoadFailure.ERROR)
        assertThat(LoadFailure.of(IllegalStateException())).isEqualTo(LoadFailure.ERROR)
    }
}
