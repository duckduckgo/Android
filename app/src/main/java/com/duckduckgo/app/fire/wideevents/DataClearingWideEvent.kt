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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DataClearingWideEvent {
    /**
     * @param browserMode The mode this clear applies to. Automatic foreground/background clearing
     * also clears Fire data as a side effect, but is attributed as [BrowserMode.REGULAR] since
     * that's the mode context it always runs in.
     * @param tabType The type of the active tab when the burn was confirmed. Only meaningful for
     * dialog-driven flows; omit where there is no active-tab context (app shortcut, auto clears,
     * fire tabs emptied).
     * @param tabCount The number of open tabs when the burn was confirmed (bucketed before sending).
     */
    suspend fun start(
        entryPoint: EntryPoint,
        clearOptions: Set<FireClearOption>,
        browserMode: BrowserMode,
        tabType: TabType? = null,
        tabCount: Int? = null,
    )

    suspend fun startLegacy(entryPoint: EntryPoint, clearWhatOption: ClearWhatOption, clearDuckAiData: Boolean)

    suspend fun stepSuccess(step: DataClearingFlowStep)

    suspend fun stepFailure(step: DataClearingFlowStep, error: Throwable)

    suspend fun finishSuccess()

    suspend fun finishFailure(error: Throwable)

    enum class EntryPoint(val value: String) {
        GRANULAR_FIRE_DIALOG("granular_fire_dialog"),
        NONGRANULAR_FIRE_DIALOG("nongranular_fire_dialog"),
        SINGLE_TAB_FIRE_DIALOG("single_tab_fire_dialog"),
        APP_SHORTCUT("app_shortcut"),
        FIRE_TABS_EMPTIED("fire_tabs_emptied"),
        AUTO_FOREGROUND("auto_foreground"),
        AUTO_BACKGROUND("auto_background"),
        LEGACY_FIRE_DIALOG("legacy_fire_dialog"),
        LEGACY_APP_SHORTCUT("legacy_app_shortcut"),
        LEGACY_AUTO_FOREGROUND("legacy_auto_foreground"),
        LEGACY_AUTO_BACKGROUND("legacy_auto_background"),
    }

    enum class TabType(val value: String) {
        WEB("web"),
        AI("ai"),
    }
}

enum class DataClearingFlowStep(val stepName: String) {
    WEB_STORAGE_CLEAR("web_storage_clear"),
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

    override suspend fun start(
        entryPoint: DataClearingWideEvent.EntryPoint,
        clearOptions: Set<FireClearOption>,
        browserMode: BrowserMode,
        tabType: DataClearingWideEvent.TabType?,
        tabCount: Int?,
    ) {
        if (!isFeatureEnabled()) return
        resetExistingFlow()

        val metadata = buildMap {
            put(KEY_CLEAR_OPTIONS, clearOptions.asMetadataValue())
            put(KEY_BROWSER_MODE, browserMode.name.lowercase())
            tabType?.let { put(KEY_TAB_TYPE, it.value) }
            tabCount?.let { put(KEY_TAB_COUNT, it.asTabCountBucket()) }
        }

        cachedFlowId = wideEventClient.flowStart(
            name = FLOW_NAME,
            flowEntryPoint = entryPoint.value,
            metadata = metadata,
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
        // The legacy clearing path (ClearDataAction/ClearPersonalDataAction) is wired exclusively to
        // @RegularMode, so every "Legacy" entry point is always a Regular-mode clear.
        start(entryPoint, legacyOptionsToClearOptions(clearWhatOption, clearDuckAiData), BrowserMode.REGULAR)
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

    // Same bucket labels as TabStatsBucketing.TAB_COUNT_BUCKETS, applied to a caller-supplied count
    // (TabStatsBucketing reads the @RegularMode repo itself, which would be wrong for Fire-mode burns).
    private fun Int.asTabCountBucket(): String = when {
        this <= 1 -> "1"
        this <= 5 -> "2-5"
        this <= 10 -> "6-10"
        this <= 20 -> "11-20"
        this <= 40 -> "21-40"
        this <= 60 -> "41-60"
        this <= 80 -> "61-80"
        else -> "81+"
    }

    private companion object {
        const val FLOW_NAME = "data-clearing"
        const val KEY_CLEAR_OPTIONS = "clear_options"
        const val KEY_TOTAL_DURATION_MS_BUCKETED = "total_duration_ms_bucketed"
        const val KEY_BROWSER_MODE = "browser_mode"
        const val KEY_TAB_TYPE = "tab_type"
        const val KEY_TAB_COUNT = "tab_count"
    }
}
