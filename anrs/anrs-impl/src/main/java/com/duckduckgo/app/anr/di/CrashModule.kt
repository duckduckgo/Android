/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.anr.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.anrs.store.CrashDatabase
import com.duckduckgo.app.anrs.store.UncaughtExceptionDao
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Qualifier

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class InternalApi

@Module
@ContributesTo(AppScope::class)
object CrashModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    @InternalApi
    fun provideCrashDatabase(context: Context): CrashDatabase {
        return Room.databaseBuilder(context, CrashDatabase::class.java, "crash_database.db")
            .addMigrations(*CrashDatabase.ALL_MIGRATIONS.toTypedArray())
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideUncaughtExceptionDao(@InternalApi crashDatabase: CrashDatabase): UncaughtExceptionDao {
        return crashDatabase.uncaughtExceptionDao()
    }
}
