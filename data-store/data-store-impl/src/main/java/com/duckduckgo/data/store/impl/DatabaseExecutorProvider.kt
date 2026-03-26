package com.duckduckgo.data.store.impl

import com.duckduckgo.data.store.api.DatabaseExecutor
import com.duckduckgo.data.store.api.DatabaseExecutor.Custom
import com.duckduckgo.data.store.api.DatabaseExecutor.Default
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface DatabaseExecutorProvider {
    fun createQueryExecutor(executor: DatabaseExecutor): Executor?
    fun createTransactionExecutor(executor: DatabaseExecutor): Executor?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDatabaseExecutorProvider @Inject constructor(
    databaseProviderFeature: DatabaseProviderFeature,
) : DatabaseExecutorProvider {

    private val defaultPoolSize = 6
    private val defaultExecutor =
        ThreadPoolExecutor(
            defaultPoolSize,
            defaultPoolSize,
            0L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(defaultPoolSize * 2),
            ThreadPoolExecutor.CallerRunsPolicy(),
        )

    private val isFeatureFlagEnabled = databaseProviderFeature.self().isEnabled()

    override fun createQueryExecutor(executor: DatabaseExecutor): Executor? {
        return when (executor) {
            is Custom -> {
                if (isFeatureFlagEnabled) {
                    createCustomExecutor(executor.queryPoolSize, executor.queryQueueSize)
                } else {
                    createLegacyQueryExecutor()
                }
            }
            Default -> {
                if (isFeatureFlagEnabled) {
                    defaultExecutor
                } else {
                    null
                }
            }
        }
    }

    override fun createTransactionExecutor(executor: DatabaseExecutor): Executor? {
        return when (executor) {
            is Custom -> {
                if (isFeatureFlagEnabled) {
                    createCustomExecutor(executor.transactionPoolSize, executor.transactionQueueSize)
                } else {
                    createLegacyTransactionExecutor()
                }
            }
            Default -> {
                if (isFeatureFlagEnabled) {
                    defaultExecutor
                } else {
                    null
                }
            }
        }
    }

    private fun createCustomExecutor(
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
            TimeUnit.SECONDS,
            queue,
            ThreadPoolExecutor.CallerRunsPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
    }

    private fun createLegacyQueryExecutor(): Executor {
        return Executors.newFixedThreadPool(4)
    }

    private fun createLegacyTransactionExecutor(): Executor {
        return Executors.newSingleThreadExecutor()
    }
}
