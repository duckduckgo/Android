/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.fire.store

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
class TabVisitedSitesModule {

    @SingleIn(AppScope::class)
    @Provides
    fun provideDatabase(context: Context): TabVisitedSitesDatabase {
        return Room.databaseBuilder(context, TabVisitedSitesDatabase::class.java, "tab_visited_sites.db")
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .fallbackToDestructiveMigration()
            .build()
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideDao(db: TabVisitedSitesDatabase): TabVisitedSitesDao = db.dao()
}
