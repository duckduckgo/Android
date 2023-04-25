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

package com.duckduckgo.cookies.impl.features.firstparty

import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy.KEEP
import androidx.work.ExistingPeriodicWorkPolicy.REPLACE
import androidx.work.WorkManager
import com.duckduckgo.cookies.api.CookiesFeatureName
import com.duckduckgo.feature.toggles.api.FeatureToggle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FirstPartyCookiesModifierWorkerSchedulerTest {

    private val mockToggle: FeatureToggle = mock()
    private val mockWorkManager: WorkManager = mock()
    private val mockOwner: LifecycleOwner = mock()

    lateinit var firstPartyCookiesModifierWorkerScheduler: FirstPartyCookiesModifierWorkerScheduler

    @Before
    fun before() {
        firstPartyCookiesModifierWorkerScheduler = FirstPartyCookiesModifierWorkerScheduler(mockWorkManager, mockToggle)
    }

    @Test
    fun whenOnStopIfFeatureEnabledThenEnqueueWorkWithReplacePolicy() {
        whenever(mockToggle.isFeatureEnabled(CookiesFeatureName.Cookie.value)).thenReturn(true)

        firstPartyCookiesModifierWorkerScheduler.onStop(mockOwner)

        verify(mockWorkManager).enqueueUniquePeriodicWork(any(), eq(REPLACE), any())
    }

    @Test
    fun whenOnStopIfFeatureNotEnabledThenDeleteTag() {
        whenever(mockToggle.isFeatureEnabled(CookiesFeatureName.Cookie.value)).thenReturn(false)

        firstPartyCookiesModifierWorkerScheduler.onStop(mockOwner)

        verify(mockWorkManager).cancelAllWorkByTag(any())
    }

    @Test
    fun whenOnStartIfFeatureEnabledThenEnqueueWorkWithKeepPolicy() {
        whenever(mockToggle.isFeatureEnabled(CookiesFeatureName.Cookie.value)).thenReturn(true)

        firstPartyCookiesModifierWorkerScheduler.onStart(mockOwner)

        verify(mockWorkManager).enqueueUniquePeriodicWork(any(), eq(KEEP), any())
    }

    @Test
    fun whenOnStartIfFeatureNotEnabledThenDeleteTag() {
        whenever(mockToggle.isFeatureEnabled(CookiesFeatureName.Cookie.value)).thenReturn(false)

        firstPartyCookiesModifierWorkerScheduler.onStart(mockOwner)

        verify(mockWorkManager).cancelAllWorkByTag(any())
    }
}
