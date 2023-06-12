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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.FAIL
import com.duckduckgo.sync.store.model.SyncAttemptState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit

class SyncSchedulerTest {

    private val syncStateRepository: SyncStateRepository = mock()
    lateinit var syncScheduler: SyncScheduler

    @Before
    fun before() {
        syncScheduler = RealSyncScheduler(syncStateRepository)
    }

    @Test
    fun whenFirstSyncThenSyncCanBeExecuted() {
        whenever(syncStateRepository.current()).thenReturn(null)

        val syncOperation = syncScheduler.scheduleOperation()

        assertEquals(syncOperation, SyncOperation.EXECUTE)
    }

    @Test
    fun whenLastSyncFailedThenSyncIsExecuted() {
        val lastSyncTimestamp = timestamp(Instant.now().minus(5, ChronoUnit.MINUTES))
        val lastSync = SyncAttempt(timestamp = lastSyncTimestamp, state = FAIL)

        whenever(syncStateRepository.current()).thenReturn(lastSync)

        val syncOperation = syncScheduler.scheduleOperation()

        assertEquals(syncOperation, SyncOperation.EXECUTE)
    }

    @Test
    fun whenLastSyncInProgressThenSyncIsDiscarded() {
        val lastSyncTimestamp = timestamp(Instant.now().minus(5, ChronoUnit.MINUTES))
        val lastSync = SyncAttempt(timestamp = lastSyncTimestamp, state = IN_PROGRESS)

        whenever(syncStateRepository.current()).thenReturn(lastSync)

        val syncOperation = syncScheduler.scheduleOperation()

        assertEquals(syncOperation, SyncOperation.DISCARD)
    }

    @Test
    fun whenLastSyncWasBeforeDebouncePeriodThenSyncIsDiscarded() {
        val lastSyncTimestamp = timestamp(Instant.now().minus(5, ChronoUnit.MINUTES))
        val lastSync = SyncAttempt(timestamp = lastSyncTimestamp, state = SUCCESS)

        whenever(syncStateRepository.current()).thenReturn(lastSync)

        val syncOperation = syncScheduler.scheduleOperation()

        assertEquals(syncOperation, SyncOperation.DISCARD)
    }

    @Test
    fun whenLastSyncWasAfterDebouncePeriodThenSyncIsDiscarded() {
        val lastSyncTimestamp = timestamp(Instant.now().minus(30, ChronoUnit.MINUTES))
        val lastSync = SyncAttempt(timestamp = lastSyncTimestamp, state = SUCCESS)

        whenever(syncStateRepository.current()).thenReturn(lastSync)

        val syncOperation = syncScheduler.scheduleOperation()

        assertEquals(syncOperation, SyncOperation.EXECUTE)
    }

    private fun timestamp(instant: Instant): String {
        return instant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
    }
}
