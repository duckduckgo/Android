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

package com.duckduckgo.adblocking.impl

import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionConfigProvider
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionSettings
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScriptletDownloadWorkerSchedulerTest {

    private val workManager: WorkManager = mock()

    private val discoverableFlow = MutableStateFlow(true)
    private val operationalFlow = MutableStateFlow(true)
    private val settingsFlow = MutableStateFlow<AdBlockingExtensionSettings?>(null)

    private val isDiscoverableToggle: Toggle = mock {
        on { enabled() } doReturn discoverableFlow
    }
    private val selfToggle: Toggle = mock {
        on { enabled() } doReturn operationalFlow
    }
    private val feature: AdBlockingExtensionFeature = mock {
        on { isDiscoverable() } doReturn isDiscoverableToggle
        on { self() } doReturn selfToggle
    }
    private val configProvider: AdBlockingExtensionConfigProvider = mock {
        on { scriptletsSettings } doReturn settingsFlow
    }
    private val lifecycleOwner: LifecycleOwner = mock()
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val scriptletsSettings = AdBlockingExtensionSettings(version = "2026.5.14", scriptlets = emptyMap())

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private fun startScheduler() {
        ScriptletDownloadWorkerScheduler(
            workManager = workManager,
            configProvider = configProvider,
            feature = feature,
            appScope = testScope,
        ).onCreate(lifecycleOwner)
    }

    @Test
    fun whenDiscoverableAndOperationalAndSettingsArePresentThenWorkIsEnqueued() {
        settingsFlow.value = scriptletsSettings

        startScheduler()

        verifyEnqueuedOnce()
    }

    @Test
    fun whenDiscoverableIsOffThenWorkIsNotEnqueued() {
        discoverableFlow.value = false
        settingsFlow.value = scriptletsSettings

        startScheduler()

        verifyNotEnqueued()
    }

    @Test
    fun whenOperationalIsOffThenWorkIsNotEnqueued() {
        operationalFlow.value = false
        settingsFlow.value = scriptletsSettings

        startScheduler()

        verifyNotEnqueued()
    }

    @Test
    fun whenSettingsAreNullThenWorkIsNotEnqueued() {
        startScheduler()

        verifyNotEnqueued()
    }

    @Test
    fun whenDiscoverableFlipsOnThenWorkIsEnqueued() {
        discoverableFlow.value = false
        settingsFlow.value = scriptletsSettings
        startScheduler()
        verifyNotEnqueued()

        discoverableFlow.value = true

        verifyEnqueuedOnce()
    }

    @Test
    fun whenOperationalFlipsOnThenWorkIsEnqueued() {
        operationalFlow.value = false
        settingsFlow.value = scriptletsSettings
        startScheduler()
        verifyNotEnqueued()

        operationalFlow.value = true

        verifyEnqueuedOnce()
    }

    @Test
    fun whenSettingsBecomeNonNullThenWorkIsEnqueued() {
        startScheduler()
        verifyNotEnqueued()

        settingsFlow.value = scriptletsSettings

        verifyEnqueuedOnce()
    }

    private fun verifyEnqueuedOnce() {
        verify(workManager).enqueueUniqueWork(
            eq(ScriptletDownloadWorkerScheduler.WORK_NAME),
            any(),
            any<OneTimeWorkRequest>(),
        )
    }

    private fun verifyNotEnqueued() {
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }
}
