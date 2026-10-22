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

package org.scrollia.app.api

import android.content.Context
import org.scrollia.app.BuildConfig
import org.scrollia.app.api.models.BookSet
import org.scrollia.app.api.models.CategoryInfo
import org.scrollia.app.api.models.ExtraInfo
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * This class is responsible for handling all the API requests related to books.
 * It uses OkHttp for making network requests and kotlinx.serialization for parsing JSON responses.
 * @param context The context of the application.
 *
 * Do not use this class directly. Use the [BookAPI] instance from dependency injection.
 */
class BookAPI(context: Context, private val preferenceUtil: PreferenceUtil) {

    // Scrollia backend (Phase 0 test API with fictional es/en books).
    //  See docs/BACKEND.md for the planned architecture.
    private val baseApiUrl = "https://api.scrollia.org/books"
    private val extraInfoUrl = "https://api.scrollia.org/extra-info"
    private val categoriesApiUrl = "https://api.scrollia.org/categories"

    private val okHttpClient by lazy {
        // Create an OkHttpClient with a cache and a network interceptor.
        val okHttpBuilder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "http-cache"), CacheInterceptor.CACHE_SIZE))
            .addNetworkInterceptor(CacheInterceptor())

        // Add logging interceptor if in debug mode.
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            okHttpBuilder.addInterceptor(logging).build()
        }
        // Finally build the OkHttpClient.
        okHttpBuilder.build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * This function fetches all the books from the API.
     * @param page The page number of the books to fetch.
     * @param bookLanguage The language of the books to fetch.
     * @return A Result object containing the BookSet if the request was successful, or an exception if it failed.
     */
    suspend fun getAllBooks(
        page: Long,
        bookLanguage: BookLanguage = BookLanguage.AllBooks,
        topic: String? = null
    ): Result<BookSet> {
        var url = "${baseApiUrl}?page=$page"
        if (bookLanguage != BookLanguage.AllBooks) {
            url += "&languages=${bookLanguage.isoCode}"
        }
        if (!topic.isNullOrEmpty()) {
            url += "&topic=$topic"
        }
        val request = Request.Builder().get().url(url).build()
        return makeApiRequest(request)
    }

    /**
     * This function searches for books based on the query provided.
     * @param query The query to search for.
     * @return A Result object containing the BookSet if the request was successful, or an exception if it failed.
     */
    suspend fun searchBooks(query: String): Result<BookSet> {
        val encodedString = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, "UTF-8")
        }
        val request = Request.Builder().get().url("${baseApiUrl}?search=$encodedString").build()
        return makeApiRequest(request)
    }

    /**
     * This function fetches a book by its ID.
     * @param bookId The ID of the book to fetch.
     * @return A Result object containing the BookSet if the request was successful, or an exception if it failed.
     */
    suspend fun getBookById(bookId: String): Result<BookSet> {
        val request = Request.Builder().get().url("${baseApiUrl}?ids=$bookId").build()
        return makeApiRequest(request)
    }

    /**
     * This function fetches books by category.
     * @param category The category of the books to fetch.
     * @param page The page number of the books to fetch.
     * @param bookLanguage The language of the books to fetch.
     * @return A Result object containing the BookSet if the request was successful, or an exception if it failed.
     */
    suspend fun getBooksByCategory(
        category: String,
        page: Long,
        bookLanguage: BookLanguage = BookLanguage.AllBooks
    ): Result<BookSet> {
        var url = "${baseApiUrl}?page=$page&topic=$category"
        if (bookLanguage != BookLanguage.AllBooks) {
            url += "&languages=${bookLanguage.isoCode}"
        }
        val request = Request.Builder().get().url(url).build()
        return makeApiRequest(request)
    }

    /**
     * Fetches the list of book categories with their book counts from the
     * Scrollia backend. Returns null if the endpoint is missing or fails
     * (e.g. older backends), so callers can fall back to the built-in
     * static category list.
     */
    suspend fun getCategories(): List<CategoryInfo>? = suspendCoroutine { continuation ->
        val request = Request.Builder().get().url(categoriesApiUrl).build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val categories = runCatching {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        json.decodeFromString(
                            ListSerializer(CategoryInfo.serializer()),
                            it.body!!.string()
                        )
                    }.getOrNull()
                    continuation.resume(categories)
                }
            }
        })
    }

    // Helper function to make API requests.
    private suspend fun makeApiRequest(request: Request): Result<BookSet> =
        suspendCoroutine { continuation ->
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(Result.failure(exception = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        continuation.resume(
                            Result.success(
                                json.decodeFromString(
                                    BookSet.serializer(),
                                    response.body!!.string()
                                ).copy(isCached = response.cacheResponse != null)
                            )
                        )
                    }
                }
            })
        }

    // Fetches extra info (cover image, page count, description) for a book
    // from the Scrollia backend, which proxies/enriches metadata server-side
    // so no third-party API key ships inside the APK.
    suspend fun getExtraInfo(bookName: String): ExtraInfo? = suspendCoroutine { continuation ->
        // Return null without any network call if the user disabled
        // extra metadata in settings.
        if (!preferenceUtil.getBoolean(PreferenceUtil.USE_GOOGLE_API_BOOL, true)) {
            continuation.resume(null)
            return@suspendCoroutine
        }

        val encodedName = URLEncoder.encode(bookName, "UTF-8")
        val request = Request.Builder().get().url("$extraInfoUrl?title=$encodedName").build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val extraInfo = runCatching {
                        json.decodeFromString(
                            ExtraInfo.serializer(),
                            response.body!!.string()
                        ).copy(isCached = response.cacheResponse != null)
                    }.getOrNull()
                    // Treat an all-empty payload as "no data".
                    continuation.resume(
                        extraInfo?.takeUnless {
                            it.coverImage.isEmpty() && it.pageCount == 0 && it.description.isEmpty()
                        }
                    )
                }
            }
        })
    }
}