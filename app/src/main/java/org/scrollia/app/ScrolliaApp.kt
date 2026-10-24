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

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import cat.ereza.customactivityoncrash.config.CaocConfig
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.scrollia.app.helpers.book.BookDownloader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


@HiltAndroidApp
class ScrolliaApp : Application(), ImageLoaderFactory {
    @Inject
    lateinit var bookDownloader: BookDownloader

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // Text selection must use LocalTextToolbar: it carries the reader's actions (Share,
        // Web search, Translate, Dictionary) and tells Pages mode that a selection is active.
        // The new context menu, on by default since Compose Foundation 1.11, ignores it.
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        CaocConfig.Builder.create().restartActivity(MainActivity::class.java).apply()
    }

    override fun newImageLoader(): ImageLoader {
        val coilOkhttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .build()

        return ImageLoader(this).newBuilder()
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .maxSizePercent(0.05)
                    .directory(cacheDir)
                    .build()
            }
            .okHttpClient(coilOkhttpClient)
            .logger(DebugLogger())
            .build()
    }
}