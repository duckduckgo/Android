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

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DataClearingWideEvent {
    suspend fun start(entryPoint: EntryPoint, clearOptions: Set<FireClearOption>)

    suspend fun startLegacy(entryPoint: EntryPoint, clearWhatOption: ClearWhatOption, clearDuckAiData: Boolean)

    suspend fun stepSuccess(step: DataClearingFlowStep)

    suspend fun stepFailure(step: DataClearingFlowStep, error: Throwable)

    suspend fun finishSuccess()

    suspend fun finishFailure(error: Throwable)

    enum class EntryPoint(val value: String) {
        GRANULAR_FIRE_DIALOG("granular_fire_dialog"),
        NONGRANULAR_FIRE_DIALOG("nongranular_fire_dialog"),
        APP_SHORTCUT("app_shortcut"),
        AUTO_FOREGROUND("auto_foreground"),
        AUTO_BACKGROUND("auto_background"),
        LEGACY_FIRE_DIALOG("legacy_fire_dialog"),
        LEGACY_APP_SHORTCUT("legacy_app_shortcut"),
        LEGACY_AUTO_FOREGROUND("legacy_auto_foreground"),
        LEGACY_AUTO_BACKGROUND("legacy_auto_background"),
    }
}

enum class DataClearingFlowStep(val stepName: String) {
    WEB_STORAGE_CLEAR("web_storage_clear"),
    WEB_STORAGE_CLEAR_GRANULAR("web_storage_clear_granular"),
    WEBVIEW_APP_WEBVIEW_CLEAR("webview_app_webview_clear"),
    WEBVIEW_DEFAULT_CLEAR("webview_default_clear"),
    INDEXEDDB_CLEAR_SELECTIVE("indexeddb_clear_selective"),
    INDEXEDDB_CLEAR_DUCKAI_ONLY("indexeddb_clear_duckai_only"),
    APP_CACHE_CLEAR("app_cache_clear"),
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataClearingWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatchers: DispatcherProvider,
) : DataClearingWideEvent {

    private var cachedFlowId: Long? = null

    override suspend fun start(entryPoint: DataClearingWideEvent.EntryPoint, clearOptions: Set<FireClearOption>) {
        if (!isFeatureEnabled()) return
        resetExistingFlow()

        cachedFlowId = wideEventClient.flowStart(
            name = FLOW_NAME,
            flowEntryPoint = entryPoint.value,
            metadata = mapOf(
                KEY_CLEAR_OPTIONS to clearOptions.asMetadataValue(),
            ),
            cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        ).getOrNull()

        cachedFlowId?.let { flowId ->
            wideEventClient.intervalStart(
                wideEventId = flowId,
                key = KEY_TOTAL_DURATION_MS_BUCKETED,
            )
        }
    }

    override suspend fun startLegacy(
        entryPoint: DataClearingWideEvent.EntryPoint,
        clearWhatOption: ClearWhatOption,
        clearDuckAiData: Boolean,
    ) {
        start(entryPoint, legacyOptionsToClearOptions(clearWhatOption, clearDuckAiData))
    }

    override suspend fun stepSuccess(step: DataClearingFlowStep) {
        if (!isFeatureEnabled()) return
        val flowId = getCurrentFlowId() ?: return

        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = "${step.stepName}_success",
            success = true,
        )
    }

    override suspend fun stepFailure(step: DataClearingFlowStep, error: Throwable) {
        if (!isFeatureEnabled()) return
        val flowId = getCurrentFlowId() ?: return
        val errorClass = error.toErrorClass()

        wideEventClient.flowStep(
            wideEventId = flowId,
            stepName = "${step.stepName}_success",
            success = false,
            metadata = mapOf("${step.stepName}_error" to errorClass),
        )
    }

    override suspend fun finishSuccess() {
        if (!isFeatureEnabled()) return
        val flowId = getCurrentFlowId() ?: return

        wideEventClient.intervalEnd(
            wideEventId = flowId,
            key = KEY_TOTAL_DURATION_MS_BUCKETED,
        )

        wideEventClient.flowFinish(
            wideEventId = flowId,
            status = FlowStatus.Success,
        )

        cachedFlowId = null
    }

    override suspend fun finishFailure(error: Throwable) {
        if (!isFeatureEnabled()) return
        val flowId = getCurrentFlowId() ?: return
        val errorClass = error.toErrorClass()

        wideEventClient.intervalEnd(
            wideEventId = flowId,
            key = KEY_TOTAL_DURATION_MS_BUCKETED,
        )

        wideEventClient.flowFinish(
            wideEventId = flowId,
            status = FlowStatus.Failure(reason = errorClass),
        )

        cachedFlowId = null
    }

    private suspend fun getCurrentFlowId(): Long? {
        if (cachedFlowId == null) {
            cachedFlowId = wideEventClient.getFlowIds(FLOW_NAME).getOrNull()?.lastOrNull()
        }
        return cachedFlowId
    }

    private suspend fun resetExistingFlow() {
        val existingFlowId = getCurrentFlowId() ?: return

        wideEventClient.flowFinish(
            wideEventId = existingFlowId,
            status = FlowStatus.Unknown,
        )

        cachedFlowId = null
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        androidBrowserConfigFeature.sendDataClearingWideEvent().isEnabled()
    }

    private fun legacyOptionsToClearOptions(
        clearWhatOption: ClearWhatOption,
        clearDuckAiData: Boolean,
    ): Set<FireClearOption> {
        return buildSet {
            when (clearWhatOption) {
                ClearWhatOption.CLEAR_NONE -> Unit
                ClearWhatOption.CLEAR_TABS_ONLY -> add(FireClearOption.TABS)
                ClearWhatOption.CLEAR_TABS_AND_DATA -> {
                    add(FireClearOption.TABS)
                    add(FireClearOption.DATA)
                }
            }
            if (clearDuckAiData) {
                add(FireClearOption.DUCKAI_CHATS)
            }
        }
    }

    private fun Set<FireClearOption>.asMetadataValue(): String {
        if (isEmpty()) return ""

        return joinToString(separator = ",") { option ->
            when (option) {
                FireClearOption.TABS -> "tabs"
                FireClearOption.DATA -> "data"
                FireClearOption.DUCKAI_CHATS -> "duckai_chats"
            }
        }
    }

    private fun Throwable.toErrorClass(): String = javaClass.simpleName

    private companion object {
        const val FLOW_NAME = "data-clearing"
        const val KEY_CLEAR_OPTIONS = "clear_options"
        const val KEY_TOTAL_DURATION_MS_BUCKETED = "total_duration_ms_bucketed"
    }
}
