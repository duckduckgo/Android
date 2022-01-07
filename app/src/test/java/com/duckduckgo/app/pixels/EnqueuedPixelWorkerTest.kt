/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.pixels

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.*
import org.junit.Before
import org.junit.Test

class EnqueuedPixelWorkerTest {
    private val workManager: WorkManager = mock()
    private val pixel: Pixel = mock()
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private lateinit var enqueuedPixelWorker: EnqueuedPixelWorker

    @Before
    fun setup() {
        enqueuedPixelWorker = EnqueuedPixelWorker(workManager, { pixel }, unsentForgetAllPixelStore)
    }

    @Test
    fun whenOnCreateAndPendingPixelCountClearDataThenScheduleWorkerToFireMf() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(2)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_CREATE)

        verify(workManager).enqueueUniquePeriodicWork(
            eq("com.duckduckgo.pixels.enqueued.worker"),
            eq(ExistingPeriodicWorkPolicy.KEEP),
            any()
        )
    }

    @Test
    fun whenOnCreateAndPendingPixelCountClearDataIsZeroThenDoNotFireMf() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(0)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_CREATE)

        verify(pixel, never()).fire(AppPixelName.FORGET_ALL_EXECUTED)
    }

    @Test
    fun whenOnStartAndLaunchByFireActionThenDoNotSendAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())

        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_CREATE)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_START)

        verify(pixel, never()).fire(AppPixelName.APP_LAUNCH)
    }

    @Test
    fun whenOnStartAndAppLaunchThenSendAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)

        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_CREATE)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_START)

        verify(pixel).fire(AppPixelName.APP_LAUNCH)
    }

    @Test
    fun whenOnStartAndLaunchByFireActionFollowedByAppLaunchThenSendOneAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())

        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_CREATE)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_START)
        enqueuedPixelWorker.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_START)

        verify(pixel).fire(AppPixelName.APP_LAUNCH)
    }
}
