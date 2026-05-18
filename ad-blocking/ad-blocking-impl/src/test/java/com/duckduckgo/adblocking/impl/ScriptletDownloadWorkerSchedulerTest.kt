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

import android.annotation.SuppressLint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionConfigProvider
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletsSettings
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class ScriptletDownloadWorkerSchedulerTest {

    private val workManager: WorkManager = mock()
    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)
    private val scriptletsFlow = MutableStateFlow<ScriptletsSettings?>(null)
    private val configProvider: AdBlockingExtensionConfigProvider = mock {
        on { scriptletsSettings } doReturn scriptletsFlow
    }
    private val settingsAdapter: JsonAdapter<ScriptletsSettings> = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ScriptletsSettings::class.java)
    private val lifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED)

    private val scriptletsSettings = ScriptletsSettings(version = "2026.5.14", scriptlets = emptyMap())

    private fun newScheduler(appScope: CoroutineScope) = ScriptletDownloadWorkerScheduler(
        workManager = workManager,
        configProvider = configProvider,
        feature = feature,
        settingsAdapter = settingsAdapter,
        appScope = appScope,
    )

    @Test
    fun whenFeatureIsOperationalAndSettingsAreEmittedThenWorkIsEnqueued() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true))
        val scheduler = newScheduler(backgroundScope)
        scheduler.onCreate(lifecycleOwner)

        scriptletsFlow.value = scriptletsSettings
        runCurrent()

        verify(workManager).enqueueUniqueWork(
            eq(ScriptletDownloadWorkerScheduler.WORK_NAME),
            any(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun whenFeatureIsNotOperationalAndSettingsAreEmittedThenWorkIsNotEnqueued() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = false))
        val scheduler = newScheduler(backgroundScope)
        scheduler.onCreate(lifecycleOwner)

        scriptletsFlow.value = scriptletsSettings
        runCurrent()

        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun whenFeatureIsOperationalAndSettingsAreNullThenWorkIsNotEnqueued() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true))
        val scheduler = newScheduler(backgroundScope)
        scheduler.onCreate(lifecycleOwner)

        runCurrent()

        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun whenFeatureFlipsFromNotOperationalToOperationalThenWorkIsEnqueued() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = false))
        scriptletsFlow.value = scriptletsSettings
        val scheduler = newScheduler(backgroundScope)
        scheduler.onCreate(lifecycleOwner)
        runCurrent()
        verify(workManager, never()).enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())

        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true))
        runCurrent()

        verify(workManager).enqueueUniqueWork(
            eq(ScriptletDownloadWorkerScheduler.WORK_NAME),
            any(),
            any<OneTimeWorkRequest>(),
        )
    }
}
