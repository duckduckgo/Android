/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.auth

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

class RealCrossProcessLockTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mock()
    private lateinit var crossProcessLock: RealCrossProcessLock

    @Before
    fun setup() {
        whenever(context.filesDir).thenReturn(temporaryFolder.root)
        crossProcessLock = RealCrossProcessLock(context, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun `when uncontended then lock is acquired and lock file is created`() = runTest {
        val result = crossProcessLock.acquire("test_key")

        assertTrue(result.isSuccess)
        assertTrue(File(temporaryFolder.root, "test_key.lock").exists())

        result.getOrThrow().close()
    }

    @Test
    fun `when lock is released then it can be acquired again`() = runTest {
        val first = crossProcessLock.acquire("test_key")
        assertTrue(first.isSuccess)
        first.getOrThrow().close()

        val second = crossProcessLock.acquire("test_key")
        assertTrue(second.isSuccess)
        second.getOrThrow().close()
    }

    @Test
    fun `when different keys then distinct lock files are used and both can be held`() = runTest {
        val first = crossProcessLock.acquire("key_a")
        val second = crossProcessLock.acquire("key_b")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertTrue(File(temporaryFolder.root, "key_a.lock").exists())
        assertTrue(File(temporaryFolder.root, "key_b.lock").exists())

        first.getOrThrow().close()
        second.getOrThrow().close()
    }

    @Test
    fun `when lock file is already locked in the same JVM then returns error`() = runTest {
        // Same-JVM overlap makes tryLock() throw OverlappingFileLockException, exercising the broad catch;
        // the cross-process tryLock-returns-null path can't be simulated in a single JVM.
        val externalChannel = RandomAccessFile(File(temporaryFolder.root, "test_key.lock"), "rw").channel
        val externalLock = externalChannel.lock()
        try {
            val result = crossProcessLock.acquire("test_key")
            assertTrue(result.isFailure)
        } finally {
            externalLock.release()
            externalChannel.close()
        }
    }

    @Test
    fun `when lock file cannot be created then returns error`() = runTest {
        whenever(context.filesDir).thenReturn(File(temporaryFolder.root, "does-not-exist"))

        val result = crossProcessLock.acquire("test_key")

        assertTrue(result.isFailure)
    }

    @Test
    fun `when handle is closed twice then second close is a no-op`() = runTest {
        val result = crossProcessLock.acquire("test_key")
        val handle = result.getOrThrow()

        handle.close()
        handle.close()
    }

    @Test
    fun `when cancellation races successful acquisition then lock is released`() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val lock = RealCrossProcessLock(
            context,
            object : DispatcherProvider {
                override fun computation(): CoroutineDispatcher = ioDispatcher
                override fun io(): CoroutineDispatcher = ioDispatcher
                override fun main(): CoroutineDispatcher = ioDispatcher
                override fun unconfined(): CoroutineDispatcher = ioDispatcher
            },
        )

        // UNDISPATCHED runs acquire() up to the withContext(io) dispatch, queueing lock acquisition;
        // the canceller is queued behind it, so cancellation lands after the lock is acquired but
        // before the result crosses the withContext boundary, where prompt cancellation discards it.
        var result: Result<Closeable>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            result = lock.acquire("test_key")
        }
        launch { job.cancel() }
        testScheduler.runCurrent()

        assertTrue(File(temporaryFolder.root, "test_key.lock").exists())
        assertNull(result)
        assertTrue(job.isCancelled)

        // A leaked lock would make this same-JVM tryLock throw OverlappingFileLockException
        RandomAccessFile(File(temporaryFolder.root, "test_key.lock"), "rw").channel.use { channel ->
            assertNotNull(runCatching { channel.tryLock() }.getOrNull())
        }
    }
}
