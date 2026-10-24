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


package org.scrollia.app.di

import android.content.Context
import org.scrollia.app.api.BookAPI
import org.scrollia.app.database.ScrolliaDatabase
import org.scrollia.app.epub.EpubParser
import org.scrollia.app.helpers.PreferenceUtil
import org.scrollia.app.helpers.book.BookCoverCache
import org.scrollia.app.helpers.book.BookDownloader
import org.scrollia.app.ui.screens.welcome.viewmodels.WelcomeDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
class MainModule {

    // Core application related. ==========================================================

    @Provides
    fun provideAppContext(@ApplicationContext context: Context) = context

    @Singleton
    @Provides
    fun provideScrolliaDatabase(@ApplicationContext context: Context) =
        ScrolliaDatabase.getInstance(context)

    @Provides
    fun provideLibraryDao(scrolliaDatabase: ScrolliaDatabase) = scrolliaDatabase.getLibraryDao()

    @Provides
    fun provideReaderDao(scrolliaDatabase: ScrolliaDatabase) = scrolliaDatabase.getReaderDao()

    @Singleton
    @Provides
    fun providePreferenceUtil(@ApplicationContext context: Context) = PreferenceUtil(context)

    // Network & Books related. ==========================================================

    @Singleton
    @Provides
    fun provideBooksApi(
        @ApplicationContext context: Context,
        preferenceUtil: PreferenceUtil
    ) = BookAPI(context, preferenceUtil)

    @Singleton
    @Provides
    fun provideBookDownloader(@ApplicationContext context: Context) = BookDownloader(context)

    @Singleton
    @Provides
    fun provideEpubParser() = EpubParser()

    @Singleton
    @Provides
    fun provideBookCoverCache(
        @ApplicationContext context: Context,
        epubParser: EpubParser
    ) = BookCoverCache(context, epubParser)

    @Provides
    @Singleton
    fun provideDataStoreRepository(
        @ApplicationContext context: Context
    ) = WelcomeDataStore(context = context)
}