/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.bandwidth.impl

import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy.KEEP
import androidx.work.WorkManager
import com.duckduckgo.bandwidth.impl.BandwidthScheduler.Companion.BANDWIDTH_WORKER_TAG
import com.duckduckgo.mobile.android.vpn.cohort.AtpCohortManager
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class BandwidthSchedulerTest {

    private val mockWorkManager: WorkManager = mock()
    private val mockAtpCohortManager: AtpCohortManager = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()

    private val testee = BandwidthScheduler(mockWorkManager, mockAtpCohortManager)

    @Test
    fun whenOnCreateAndIsInAppTPBetaThenScheduleWork() {
        whenever(mockAtpCohortManager.getCohort()).thenReturn("cohort")

        testee.onCreate(mockLifecycleOwner)

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(BANDWIDTH_WORKER_TAG),
            eq(KEEP),
            any()
        )
    }

    @Test
    fun whenOnCreateAndIsNotInAppTPBetaThenDoNotScheduleWork() {
        whenever(mockAtpCohortManager.getCohort()).thenReturn(null)

        testee.onCreate(mockLifecycleOwner)

        verifyNoInteractions(mockWorkManager)
    }
}
