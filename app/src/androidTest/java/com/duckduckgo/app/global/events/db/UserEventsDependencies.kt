/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.events.db;

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.bookmarks.model.FavoritesDataRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.onboarding.store.AppUserStageStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.VariantManager
import com.nhaarman.mockitokotlin2.mock
import dagger.Lazy

@Suppress("LeakingThis")
// Temporary class to make easier to provide dependencies until we can use dagger from Test classes
open class UserEventsDependencies(context: Context, dispatcherProvider: DispatcherProvider) {

    open val variantManager: VariantManager = mock()

    open val faviconManager: FaviconManager = mock()

    open val lazyFaviconManager = Lazy { faviconManager }

    open val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    open val userEventsStore: UserEventsStore = AppUserEventsStore(db.userEventsDao(), dispatcherProvider)

    open val userStageStore: UserStageStore = AppUserStageStore(db.userStageDao(), dispatcherProvider)

    open val favoritesRepository: FavoritesRepository = FavoritesDataRepository(db.favoritesDao(), lazyFaviconManager)

    open val userEventsRepository: UserEventsRepository = AppUserEventsRepository(
        userEventsStore,
        userStageStore,
        favoritesRepository,
        DuckDuckGoUrlDetector(),
        lazyFaviconManager,
        dispatcherProvider,
        variantManager
    )
}
