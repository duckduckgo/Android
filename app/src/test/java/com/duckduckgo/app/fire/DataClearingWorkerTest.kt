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

package com.duckduckgo.app.fire

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.coroutines.cancellation.CancellationException

class DataClearingWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val settingsDataStore: SettingsDataStore = mock()
    private val dataClearing: AutomaticDataClearing = mock()
    private val fireDataStore: FireDataStore = mock()
    private val dataClearingWideEvent: DataClearingWideEvent = mock()

    private lateinit var worker: DataClearingWorker

    @Before
    fun setup() = runTest {
        whenever(fireDataStore.getAutomaticClearOptions()).thenReturn(setOf(FireClearOption.TABS))

        val context: Context = mock()
        worker = TestListenableWorkerBuilder<DataClearingWorker>(context = context).build()
        worker.settingsDataStore = settingsDataStore
        worker.dataClearing = dataClearing
        worker.fireDataStore = fireDataStore
        worker.dataClearingWideEvent = dataClearingWideEvent
        worker.dispatchers = coroutineRule.testDispatcherProvider
    }

    @Test
    fun `when clear completes then flow finishes successfully`() = runTest {
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(any())).thenReturn(true)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(dataClearingWideEvent).start(
            entryPoint = DataClearingWideEvent.EntryPoint.AUTO_BACKGROUND,
            clearOptions = setOf(FireClearOption.TABS),
            browserMode = BrowserMode.REGULAR,
        )
        verify(dataClearingWideEvent).finishSuccess()
    }

    @Test
    fun `when clear is cancelled then flow finishes as cancelled with the worker stop reason`() = runTest {
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(any())).thenAnswer { throw CancellationException() }

        val error = runCatching { worker.doWork() }.exceptionOrNull()

        assertTrue(error is CancellationException)
        verify(dataClearingWideEvent).finishCancelled(WorkInfo.STOP_REASON_NOT_STOPPED)
        verify(dataClearingWideEvent, never()).finishFailure(any<Throwable>())
    }

    @Test
    fun `when clear fails then flow finishes as failure`() = runTest {
        val failure = IllegalStateException()
        whenever(dataClearing.clearDataUsingAutomaticFireOptions(any())).thenAnswer { throw failure }

        val error = runCatching { worker.doWork() }.exceptionOrNull()

        assertEquals(failure, error)
        verify(dataClearingWideEvent).finishFailure(failure)
        verify(dataClearingWideEvent, never()).finishCancelled(any())
    }
}
