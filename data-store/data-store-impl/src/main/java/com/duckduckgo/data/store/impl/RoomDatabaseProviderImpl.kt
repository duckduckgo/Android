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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RoomDatabaseProviderImpl @Inject constructor(
    private val context: Context,
    databaseProviderFeature: DatabaseProviderFeature,
) : DatabaseProvider {

    private val featureFlagEnabled: Boolean = databaseProviderFeature.self().isEnabled()

    private val defaultPoolSize = 6
    private val defaultExecutor: Executor by lazy {
        ThreadPoolExecutor(
            defaultPoolSize,
            defaultPoolSize,
            0L,
            SECONDS,
            ArrayBlockingQueue(defaultPoolSize * 2),
            ThreadPoolExecutor.CallerRunsPolicy(),
        )
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
                        (config.executor as? Custom)?.let { executor ->
                            val queryExecutor = if (featureFlagEnabled) {
                                createExecutor(
                                    executor.queryPoolSize,
                                    executor.queryQueueSize,
                                )
                            } else {
                                Executors.newFixedThreadPool(4)
                            }

                            val transactionExecutor = if (featureFlagEnabled) {
                                createExecutor(
                                    executor.transactionPoolSize,
                                    executor.transactionQueueSize,
                                )
                            } else {
                                Executors.newSingleThreadExecutor()
                            }
                            setQueryExecutor(queryExecutor)
                            setTransactionExecutor(transactionExecutor)
                        }
                    }
                    Default -> {
                        if (featureFlagEnabled) {
                            setQueryExecutor(defaultExecutor)
                            setTransactionExecutor(defaultExecutor)
                        }
                    }
                }
            }
            .build()
    }

    private fun createExecutor(
        poolSize: Int,
        queueSize: Int,
    ): Executor {
        val queue = object : ArrayBlockingQueue<Runnable>(queueSize) {
            override fun add(element: Runnable): Boolean {
                return super.add(element)
            }
        }

        return ThreadPoolExecutor(
            poolSize,
            poolSize,
            60L,
            SECONDS,
            queue,
            ThreadPoolExecutor.CallerRunsPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }
}
