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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.adblocking.impl.domain.ScriptletUpdateResult
import com.duckduckgo.adblocking.impl.domain.ScriptletUpdater
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletEntry
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletsSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class ScriptletDownloadWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val updater: ScriptletUpdater = mock()
    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)
    private val settingsAdapter: JsonAdapter<ScriptletsSettings> = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ScriptletsSettings::class.java)

    private val settings = ScriptletsSettings(
        version = "2026.3.9",
        scriptlets = mapOf(
            "scriptlets/isolated/ublock-filters.js" to ScriptletEntry("https://cdn.example/isolated.js", "iso-sig"),
        ),
    )

    @Before
    fun setup() {
        feature.isDiscoverable().setRawStoredState(Toggle.State(remoteEnableState = true))
    }

    @Test
    fun whenInputDataHasNoSettingsThenDoWorkReturnsFailure() = runTest {
        val result = newWorker(inputData = null).doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun whenInputDataHasMalformedSettingsJsonThenDoWorkReturnsFailure() = runTest {
        val result = newWorker(inputData = "{ not valid json").doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun whenUpdaterReportsSuccessThenDoWorkPassesDeserialisedSettingsAndReturnsSuccess() = runTest {
        whenever(updater.update(eq(settings))).thenReturn(ScriptletUpdateResult.Success)

        val result = newWorker(inputData = settingsAdapter.toJson(settings)).doWork()

        assertEquals(Result.success(), result)
        verify(updater).update(eq(settings))
    }

    @Test
    fun whenUpdaterReportsRetryThenDoWorkReturnsRetry() = runTest {
        whenever(updater.update(any())).thenReturn(ScriptletUpdateResult.Retry)

        val result = newWorker(inputData = settingsAdapter.toJson(settings)).doWork()

        assertEquals(Result.retry(), result)
    }

    @Test
    fun whenKillSwitchIsOffThenDoWorkReturnsSuccessWithoutCallingUpdater() = runTest {
        feature.isDiscoverable().setRawStoredState(Toggle.State(remoteEnableState = false))

        val result = newWorker(inputData = settingsAdapter.toJson(settings)).doWork()

        assertEquals(Result.success(), result)
        verify(updater, never()).update(any())
    }

    private fun newWorker(inputData: String?): ScriptletDownloadWorker {
        val data = if (inputData == null) {
            androidx.work.Data.EMPTY
        } else {
            workDataOf(ScriptletDownloadWorker.KEY_SETTINGS to inputData)
        }
        return TestListenableWorkerBuilder<ScriptletDownloadWorker>(context, inputData = data).build().also {
            it.updater = updater
            it.settingsAdapter = settingsAdapter
            it.feature = feature
            it.dispatchers = coroutineRule.testDispatcherProvider
        }
    }
}
