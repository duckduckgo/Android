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

package com.duckduckgo.data.store.impl

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RoomDatabaseProviderImpl @Inject constructor(
    private val context: Context,
    private val roomDatabaseBuilderFactory: RoomDatabaseBuilderFactory,
    private val lazyDatabaseExecutorProvider: Lazy<DatabaseExecutorProvider>,
) : DatabaseProvider {

    override fun<T : RoomDatabase> buildRoomDatabase(
        klass: Class<T>,
        name: String,
        config: RoomDatabaseConfig,
    ): T {
        return roomDatabaseBuilderFactory.createBuilder(context, klass, name)
            .apply {
                applyMigrations(config.migrations)
                applyFallbackToDestructiveMigration(config.fallbackToDestructiveMigration)
                applyOpenHelperFactory(config.openHelperFactory)
                applyMultiInstanceInvalidation(config.enableMultiInstanceInvalidation)
                applyJournalMode(config.journalMode)
                applyCallbacks(config.callbacks)
                applyFallbackToDestructiveMigrationFromVersion(config.fallbackToDestructiveMigrationFromVersion)
                applyExecutors(config.executor)
            }
            .build()
    }

    private fun RoomDatabase.Builder<*>.applyMigrations(migrations: List<Migration>) {
        if (migrations.isNotEmpty()) {
            addMigrations(*migrations.toTypedArray())
        }
    }

    private fun RoomDatabase.Builder<*>.applyFallbackToDestructiveMigration(fallback: Boolean) {
        if (fallback) {
            fallbackToDestructiveMigration()
        }
    }

    private fun RoomDatabase.Builder<*>.applyOpenHelperFactory(factory: SupportSQLiteOpenHelper.Factory?) {
        factory?.let { openHelperFactory(it) }
    }

    private fun RoomDatabase.Builder<*>.applyMultiInstanceInvalidation(enable: Boolean) {
        if (enable) {
            enableMultiInstanceInvalidation()
        }
    }

    private fun RoomDatabase.Builder<*>.applyJournalMode(journalMode: RoomDatabase.JournalMode?) {
        journalMode?.let { setJournalMode(it) }
    }

    private fun RoomDatabase.Builder<*>.applyCallbacks(callbacks: List<RoomDatabase.Callback>) {
        callbacks.forEach { addCallback(it) }
    }

    private fun RoomDatabase.Builder<*>.applyFallbackToDestructiveMigrationFromVersion(versions: List<Int>) {
        if (versions.isNotEmpty()) {
            fallbackToDestructiveMigrationFrom(*versions.toIntArray())
        }
    }

    private fun RoomDatabase.Builder<*>.applyExecutors(executor: com.duckduckgo.data.store.api.DatabaseExecutor) {
        // Wrap in lazy-initialising proxies so the feature-flag read (SharedPreferences) inside
        // RealDatabaseExecutorProvider's constructor is deferred until the first actual DB query,
        // which Room runs on a background thread — not at DI construction time on the main thread.
        setQueryExecutor(LazyExecutor { lazyDatabaseExecutorProvider.get().createQueryExecutor(executor) })
        setTransactionExecutor(LazyExecutor { lazyDatabaseExecutorProvider.get().createTransactionExecutor(executor) })
    }
}

/**
 * An [Executor] that defers resolving its delegate to the first [execute] call.
 * This avoids triggering SharedPreferences reads during DI graph construction on the main thread.
 * When the resolved delegate is null (feature flag disabled for [DatabaseExecutor.Default]),
 * a cached thread pool is used as a fallback equivalent to Room's own background thread pool.
 */
internal class LazyExecutor(init: () -> Executor?) : Executor {
    internal val delegate: Executor? by lazy { init() }
    private val fallback: Executor by lazy { Executors.newCachedThreadPool() }
    override fun execute(command: Runnable) {
        (delegate ?: fallback).execute(command)
    }
}
