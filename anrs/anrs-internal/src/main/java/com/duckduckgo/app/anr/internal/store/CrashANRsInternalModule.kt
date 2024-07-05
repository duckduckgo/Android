/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.anr.internal.store

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class CrashANRsInternalModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAnrDatabase(context: Context): CrashANRsInternalDatabase {
        return Room.databaseBuilder(context, CrashANRsInternalDatabase::class.java, "crash_anr_internal_database.db")
            .addMigrations(*CrashANRsInternalDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }
}
