/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.fire.wideevents

import android.annotation.SuppressLint
import com.duckduckgo.app.fire.wideevents.DataClearingWideEvent.EntryPoint
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*

class DataClearingWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    @SuppressLint("DenyListedApi")
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature =
        FakeFeatureToggleFactory
            .create(AndroidBrowserConfigFeature::class.java)
            .apply { sendDataClearingWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var dataClearingWideEvent: DataClearingWideEventImpl

    @Before
    fun setup() {
        dataClearingWideEvent = DataClearingWideEventImpl(
            wideEventClient = wideEventClient,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `start starts a new flow with entry point and clear options`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        val clearOptions = setOf(FireClearOption.TABS, FireClearOption.DATA)
        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, clearOptions)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "granular_fire_dialog",
            metadata = mapOf("clear_options" to "tabs,data"),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
        verify(wideEventClient).intervalStart(wideEventId = 123L, key = "total_duration_ms_bucketed")
    }

    @Test
    fun `start with empty clear options sets empty metadata value`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(456L))

        dataClearingWideEvent.start(EntryPoint.APP_SHORTCUT, emptySet())

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "app_shortcut",
            metadata = mapOf("clear_options" to ""),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `start with duckai chats option includes duckai_chats in metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(789L))

        val clearOptions = setOf(FireClearOption.DUCKAI_CHATS)
        dataClearingWideEvent.start(EntryPoint.NONGRANULAR_FIRE_DIALOG, clearOptions)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "nongranular_fire_dialog",
            metadata = mapOf("clear_options" to "duckai_chats"),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `start resets existing flow before starting new one`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.start(EntryPoint.APP_SHORTCUT, setOf(FireClearOption.DATA))

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Unknown)
    }

    @Test
    fun `startLegacy with CLEAR_NONE results in empty options`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        dataClearingWideEvent.startLegacy(EntryPoint.LEGACY_FIRE_DIALOG, ClearWhatOption.CLEAR_NONE, clearDuckAiData = false)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "legacy_fire_dialog",
            metadata = mapOf("clear_options" to ""),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `startLegacy with CLEAR_TABS_ONLY results in tabs option`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(200L))

        dataClearingWideEvent.startLegacy(EntryPoint.LEGACY_APP_SHORTCUT, ClearWhatOption.CLEAR_TABS_ONLY, clearDuckAiData = false)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "legacy_app_shortcut",
            metadata = mapOf("clear_options" to "tabs"),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `startLegacy with CLEAR_TABS_AND_DATA results in tabs and data options`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(300L))

        dataClearingWideEvent.startLegacy(EntryPoint.LEGACY_AUTO_FOREGROUND, ClearWhatOption.CLEAR_TABS_AND_DATA, clearDuckAiData = false)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "legacy_auto_foreground",
            metadata = mapOf("clear_options" to "tabs,data"),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `startLegacy with clearDuckAiData adds duckai_chats option`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(400L))

        dataClearingWideEvent.startLegacy(EntryPoint.LEGACY_AUTO_BACKGROUND, ClearWhatOption.CLEAR_TABS_ONLY, clearDuckAiData = true)

        verify(wideEventClient).flowStart(
            name = "data-clearing",
            flowEntryPoint = "legacy_auto_background",
            metadata = mapOf("clear_options" to "tabs,duckai_chats"),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )
    }

    @Test
    fun `stepSuccess sends successful step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(500L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).flowStep(
            wideEventId = 500L,
            stepName = "web_storage_clear_success",
            success = true,
        )
    }

    @Test
    fun `stepSuccess with different steps sends correct step names`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(501L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEBVIEW_DEFAULT_CLEAR)

        verify(wideEventClient).flowStep(
            wideEventId = 501L,
            stepName = "webview_default_clear_success",
            success = true,
        )
    }

    @Test
    fun `stepFailure sends failed step with error class`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(600L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.DATA))
        dataClearingWideEvent.stepFailure(DataClearingFlowStep.APP_CACHE_CLEAR, IllegalStateException("error"))

        verify(wideEventClient).flowStep(
            wideEventId = 600L,
            stepName = "app_cache_clear_success",
            success = false,
            metadata = mapOf("app_cache_clear_error" to "IllegalStateException"),
        )
    }

    @Test
    fun `finishSuccess ends interval and finishes flow with success`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(700L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.finishSuccess()

        verify(wideEventClient).intervalEnd(wideEventId = 700L, key = "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(wideEventId = 700L, status = FlowStatus.Success)
    }

    @Test
    fun `finishSuccess clears cached flow id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(701L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.finishSuccess()

        reset(wideEventClient)
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        // After cache is cleared, should call getFlowIds again
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).getFlowIds("data-clearing")
    }

    @Test
    fun `finishFailure ends interval and finishes flow with failure`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(800L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.finishFailure(RuntimeException("failure"))

        verify(wideEventClient).intervalEnd(wideEventId = 800L, key = "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(
            wideEventId = 800L,
            status = FlowStatus.Failure(reason = "RuntimeException"),
        )
    }

    @Test
    fun `finishFailure clears cached flow id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(801L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.finishFailure(IllegalArgumentException("error"))

        reset(wideEventClient)
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        // After cache is cleared, should call getFlowIds again
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).getFlowIds("data-clearing")
    }

    @Test
    fun `getCurrentFlowId returns cached flow id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(900L))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        verify(wideEventClient).getFlowIds(any())
        Mockito.clearInvocations(wideEventClient)

        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEBVIEW_APP_WEBVIEW_CLEAR)

        // getFlowIds should not be called because flow id is cached from start
        verify(wideEventClient, never()).getFlowIds(any())
    }

    @Test
    fun `getCurrentFlowId fetches from client when not cached`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(901L)))

        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).getFlowIds("data-clearing")
        verify(wideEventClient).flowStep(
            wideEventId = 901L,
            stepName = "web_storage_clear_success",
            success = true,
        )
    }

    @Test
    fun `getCurrentFlowId returns last flow id when multiple exist`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(100L, 200L, 300L)))

        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).flowStep(
            wideEventId = 300L,
            stepName = "web_storage_clear_success",
            success = true,
        )
    }

    @Test
    fun `no flow id available results in no step interactions`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)

        verify(wideEventClient).getFlowIds("data-clearing")
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `no flow id available results in no finish interactions`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        dataClearingWideEvent.finishSuccess()

        verify(wideEventClient).getFlowIds("data-clearing")
        verifyNoMoreInteractions(wideEventClient)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() = runTest {
        androidBrowserConfigFeature.sendDataClearingWideEvent().setRawStoredState(Toggle.State(false))

        dataClearingWideEvent.start(EntryPoint.GRANULAR_FIRE_DIALOG, setOf(FireClearOption.TABS))
        dataClearingWideEvent.startLegacy(EntryPoint.LEGACY_FIRE_DIALOG, ClearWhatOption.CLEAR_TABS_AND_DATA, clearDuckAiData = true)
        dataClearingWideEvent.stepSuccess(DataClearingFlowStep.WEB_STORAGE_CLEAR)
        dataClearingWideEvent.stepFailure(DataClearingFlowStep.WEB_STORAGE_CLEAR, RuntimeException("error"))
        dataClearingWideEvent.finishSuccess()
        dataClearingWideEvent.finishFailure(RuntimeException("error"))

        verifyNoInteractions(wideEventClient)
    }
}
