package org.scrollia.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import org.scrollia.app.api.HttpStatusException
import org.scrollia.app.api.MalformedResponseException
import org.scrollia.app.api.models.CategoryInfo
import org.scrollia.app.helpers.ContentState
import org.scrollia.app.ui.screens.categories.composables.BookCategories
import org.scrollia.app.ui.screens.categories.viewmodels.CategoriesState

class CategoriesStateTest {

    @Test
    fun `initial state is Loading`() {
        assertThat(CategoriesState().content).isEqualTo(ContentState.LOADING)
    }

    @Test
    fun `only categories with books are shown`() {
        val state = CategoriesState.from(
            Result.success(
                listOf(
                    CategoryInfo(category = "fiction", count = 12),
                    CategoryInfo(category = "law", count = 0),
                    CategoryInfo(category = "history", count = 3)
                )
            )
        )
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.categories.map { it.category })
            .containsExactly("fiction", "history").inOrder()
    }

    @Test
    fun `no category with books is Empty`() {
        val state = CategoriesState.from(
            Result.success(listOf(CategoryInfo(category = "law", count = 0)))
        )
        assertThat(state.content).isEqualTo(ContentState.EMPTY)
    }

    @Test
    fun `a backend without the endpoint falls back to the built-in list`() {
        val state = CategoriesState.from(Result.failure(HttpStatusException(404)))
        assertThat(state.content).isEqualTo(ContentState.RESULTS)
        assertThat(state.categories).containsExactlyElementsIn(BookCategories.ALL)
        assertThat(state.failure).isNull()
    }

    @Test
    fun `network failure is Offline, not a static list`() {
        val state = CategoriesState.from(Result.failure(IOException("no route")))
        assertThat(state.content).isEqualTo(ContentState.OFFLINE)
        assertThat(state.categories).isEmpty()
    }

    @Test
    fun `server and parse failures are Error`() {
        for (error in listOf(HttpStatusException(500), MalformedResponseException(null))) {
            val state = CategoriesState.from(Result.failure(error))
            assertThat(state.content).isEqualTo(ContentState.ERROR)
            assertThat(state.categories).isEmpty()
        }
    }
}
