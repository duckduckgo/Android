package com.duckduckgo.data.store.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.data.store.api.DatabaseExecutor
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealDatabaseExecutorProviderTest {

    private val databaseProviderFeature: DatabaseProviderFeature = FakeFeatureToggleFactory.create(DatabaseProviderFeature::class.java)
    private lateinit var subject: RealDatabaseExecutorProvider

    @Test
    fun whenFeatureFlagEnabledAndDefaultExecutorThenSameExecutorInstanceIsReturned() = runTest {
        prepareSubject(true)

        val queryExecutor1 = subject.createQueryExecutor(DatabaseExecutor.Default)
        val queryExecutor2 = subject.createQueryExecutor(DatabaseExecutor.Default)
        val transactionExecutor1 = subject.createTransactionExecutor(DatabaseExecutor.Default)
        val transactionExecutor2 = subject.createTransactionExecutor(DatabaseExecutor.Default)

        assertNotNull(queryExecutor1)
        assertNotNull(transactionExecutor1)
        assertSame(queryExecutor1, queryExecutor2)
        assertSame(transactionExecutor1, transactionExecutor2)
        assertSame(queryExecutor1, transactionExecutor1) // Should be the same cached instance
    }

    @Test
    fun whenFeatureFlagDisabledAndDefaultExecutorThenNullIsReturned() = runTest {
        prepareSubject(false)

        val queryExecutor = subject.createQueryExecutor(DatabaseExecutor.Default)
        val transactionExecutor = subject.createTransactionExecutor(DatabaseExecutor.Default)

        assertNull(queryExecutor)
        assertNull(transactionExecutor)
    }

    @Test
    fun whenFeatureFlagEnabledAndCustomExecutorThenCustomExecutorsAreCreated() = runTest {
        prepareSubject(true)
        val customExecutor = DatabaseExecutor.Custom(
            transactionPoolSize = 2,
            queryPoolSize = 4,
            transactionQueueSize = 6,
            queryQueueSize = 10,
        )

        val queryExecutor = subject.createQueryExecutor(customExecutor)
        val transactionExecutor = subject.createTransactionExecutor(customExecutor)

        assertNotNull(queryExecutor)
        assertNotNull(transactionExecutor)
        assertTrue(queryExecutor is ThreadPoolExecutor)
        assertTrue(transactionExecutor is ThreadPoolExecutor)

        val queryThreadPool = queryExecutor as ThreadPoolExecutor
        val transactionThreadPool = transactionExecutor as ThreadPoolExecutor

        assertEquals(4, queryThreadPool.corePoolSize)
        assertEquals(4, queryThreadPool.maximumPoolSize)
        assertEquals(10, queryThreadPool.queue.remainingCapacity() + queryThreadPool.queue.size)

        assertEquals(2, transactionThreadPool.corePoolSize)
        assertEquals(2, transactionThreadPool.maximumPoolSize)
        assertEquals(6, transactionThreadPool.queue.remainingCapacity() + transactionThreadPool.queue.size)
    }

    @Test
    fun whenFeatureFlagDisabledAndCustomExecutorThenLegacyExecutorsAreCreated() = runTest {
        prepareSubject(false)
        val customExecutor = DatabaseExecutor.Custom(
            transactionPoolSize = 2,
            queryPoolSize = 4,
        )

        val queryExecutor = subject.createQueryExecutor(customExecutor)
        val transactionExecutor = subject.createTransactionExecutor(customExecutor)

        assertNotNull(queryExecutor)
        assertNotNull(transactionExecutor)
        assertTrue(queryExecutor is Executor)
        assertTrue(transactionExecutor is Executor)
    }

    @Test
    fun whenCustomExecutorsCreatedMultipleTimesThenDifferentInstancesAreReturned() = runTest {
        prepareSubject(true)
        val customExecutor = DatabaseExecutor.Custom(
            transactionPoolSize = 2,
            queryPoolSize = 4,
        )

        val queryExecutor1 = subject.createQueryExecutor(customExecutor)
        val queryExecutor2 = subject.createQueryExecutor(customExecutor)
        val transactionExecutor1 = subject.createTransactionExecutor(customExecutor)
        val transactionExecutor2 = subject.createTransactionExecutor(customExecutor)

        assertNotNull(queryExecutor1)
        assertNotNull(queryExecutor2)
        assertNotNull(transactionExecutor1)
        assertNotNull(transactionExecutor2)
        assertNotEquals(queryExecutor1, queryExecutor2)
        assertNotEquals(transactionExecutor1, transactionExecutor2)
    }

    private fun prepareSubject(flagEnabled: Boolean) {
        databaseProviderFeature.self().setRawStoredState(State(enable = flagEnabled))
        subject = RealDatabaseExecutorProvider(databaseProviderFeature)
    }
}
