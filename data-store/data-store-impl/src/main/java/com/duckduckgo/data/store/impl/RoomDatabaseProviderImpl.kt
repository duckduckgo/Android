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
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.data.store.api.DatabaseExecutor.Custom
import com.duckduckgo.data.store.api.DatabaseExecutor.Default
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RoomDatabaseProviderImpl @Inject constructor(
    private val context: Context,
) : DatabaseProvider {

    val executor: Executor by lazy {
        Executors.newFixedThreadPool(6)
    }

    override fun<T : RoomDatabase> buildRoomDatabase(
        klass: Class<T>,
        name: String,
        config: RoomDatabaseConfig,
    ): T {
        return Room.databaseBuilder(context, klass, name)
            .apply {
                if (config.migrations.isNotEmpty()) {
                    addMigrations(*config.migrations.toTypedArray())
                }

                if (config.fallbackToDestructiveMigration) {
                    fallbackToDestructiveMigration()
                }

                config.openHelperFactory?.let { factory ->
                    openHelperFactory(factory)
                }

                if (config.enableMultiInstanceInvalidation) {
                    enableMultiInstanceInvalidation()
                }

                config.journalMode?.let { mode ->
                    setJournalMode(mode)
                }

                config.callbacks.forEach { callback ->
                    addCallback(callback)
                }

                if (config.fallbackToDestructiveMigrationFromVersion.isNotEmpty()) {
                    fallbackToDestructiveMigrationFrom(*config.fallbackToDestructiveMigrationFromVersion.toIntArray())
                }
                when (config.executor) {
                    is Custom -> {
                        setQueryExecutor(createExecutor((config.executor as Custom).queryPoolSize))
                        setTransactionExecutor(createExecutor((config.executor as Custom).transactionPoolSize))
                    }
                    Default -> {
                        setQueryExecutor(executor)
                        setTransactionExecutor(executor)
                    }
                }
            }
            .build()
    }

    private fun createExecutor(
        poolSize: Int,
    ): Executor {
        return ThreadPoolExecutor(
            poolSize,
            poolSize,
            60L,
            SECONDS,
            LinkedBlockingQueue(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}
