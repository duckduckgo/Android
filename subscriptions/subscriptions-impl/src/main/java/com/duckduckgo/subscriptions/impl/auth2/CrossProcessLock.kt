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

package com.duckduckgo.subscriptions.impl.auth2

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface CrossProcessLock {
    /**
     * Acquires an exclusive cross-process lock identified by [key], waiting up to [timeout].
     * Returns a [Closeable] that releases the lock, or a failure if the lock could not be acquired
     * ([TimeoutCancellationException] if [timeout] elapsed).
     * Callers MUST serialize acquisitions of the same [key] in-process.
     */
    suspend fun acquire(key: String, timeout: Duration = 60.seconds): Result<Closeable>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealCrossProcessLock @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : CrossProcessLock {

    override suspend fun acquire(key: String, timeout: Duration): Result<Closeable> = try {
        withTimeout(timeout) {
            withContext(dispatcherProvider.io()) {
                val channel = RandomAccessFile(File(context.filesDir, "$key.lock"), "rw").channel
                try {
                    var lock = channel.tryLock()
                    while (lock == null) {
                        delay(POLL_INTERVAL)
                        lock = channel.tryLock()
                    }
                    logcat(tag = LOG_TAG) { "Lock acquired: $key" }
                    Result.success(FileLockHandle(key, channel, lock))
                } catch (e: Throwable) {
                    runCatching { channel.close() }
                    throw e
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        logcat(tag = LOG_TAG, priority = WARN) { "Timed out acquiring lock: $key" }
        Result.failure(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat(tag = LOG_TAG, priority = WARN) { "Lock acquisition failed: $key ${e.asLog()}" }
        Result.failure(e)
    }

    private class FileLockHandle(
        private val key: String,
        private val channel: FileChannel,
        private val lock: FileLock,
    ) : Closeable {
        override fun close() {
            runCatching { lock.release() }
            runCatching { channel.close() }
            logcat(tag = LOG_TAG) { "Lock released: $key" }
        }
    }

    private companion object {
        val POLL_INTERVAL = 100.milliseconds
        const val LOG_TAG = "CrossProcessLock"
    }
}
