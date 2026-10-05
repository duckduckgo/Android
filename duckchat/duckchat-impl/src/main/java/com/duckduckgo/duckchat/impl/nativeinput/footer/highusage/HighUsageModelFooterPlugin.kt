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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.impl.nativeinput.footer.USAGE_NOTICE_FOOTER_CATEGORY
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningExposure
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningMeasurements
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputFooterPlugin::class,
    featureName = "pluginHighUsageModelNativeInputFooter",
    parentFeatureName = "pluginPointNativeInputFooter",
)
class HighUsageModelFooterPlugin @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val duckChatFeature: DuckChatFeature,
    private val resolver: HighUsageModelNoticeResolver,
    private val dismissalStore: HighUsageModelNoticeDismissalStore,
    private val measurements: UsageWarningMeasurements,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputFooterPlugin {

    override val priority: Int = 100
    override val category: String = USAGE_NOTICE_FOOTER_CATEGORY

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter {
        val footerView = HighUsageModelFooterView(context)
        val locallyDismissedModelIds = MutableStateFlow<Set<String>>(emptySet())
        val usageWarningsEnabled = duckChatFeature.duckAiUsageWarnings().enabled()
        val measurement = measurements.forHost(host) { DuckChatPixelSurface.from(hostContext.value.inputContext) }
        var lastContext: NativeInputFooterContext? = null
        var lastSelectedModelId: String? = null
        var currentExposure: UsageWarningExposure? = null
        var isDisplayed = false

        fun dismissNotice(notice: HighUsageModelNotice) {
            if (notice.modelId in locallyDismissedModelIds.value) return
            measurement.warningDismissed()
            locallyDismissedModelIds.value += notice.modelId
            appCoroutineScope.launch {
                dismissalStore.dismiss(notice.modelId)
            }
        }

        val persistedDismissedModelIds = combine(
            hostContext,
            usageWarningsEnabled,
        ) { footerContext, enabled ->
            enabled &&
                footerContext.isDuckAiSelected &&
                !footerContext.isEditing &&
                footerContext.browserMode != BrowserMode.FIRE &&
                footerContext.isInputFocused
        }
            .distinctUntilChanged()
            .flatMapLatest { shouldObserve ->
                if (shouldObserve) {
                    dismissalStore.dismissedModelIds.map<Set<String>, Set<String>?> { it }
                } else {
                    flowOf(null)
                }
            }

        val state = combine(
            modelManager.modelState,
            hostContext,
            persistedDismissedModelIds,
            locallyDismissedModelIds,
            usageWarningsEnabled,
        ) { modelState, footerContext, persistedDismissedModelIds, localDismissedModelIds, usageWarningsEnabled ->
            val previousContext = lastContext
            lastContext = footerContext
            val previousModelId = lastSelectedModelId
            lastSelectedModelId = modelState.selectedModelId ?: previousModelId
            // A prompt is attributed before the focus loss that follows it, even when both arrive in one emission.
            if (previousContext != null && footerContext.promptSubmissions > previousContext.promptSubmissions) measurement.promptSubmitted()
            if (previousModelId != null && modelState.selectedModelId != null && modelState.selectedModelId != previousModelId) {
                measurement.modelSwitched()
            }
            if (usageWarningsEnabled && persistedDismissedModelIds != null) {
                resolver.resolve(modelState, footerContext, persistedDismissedModelIds + localDismissedModelIds)
            } else {
                measurement.inputSessionEnded()
                null
            }
        }.map { notice ->
            if (notice != null) {
                val exposure = UsageWarningExposure.ofHighUsageModel(notice.modelId)
                if (isDisplayed && exposure != currentExposure) measurement.cardBecameVisible(exposure)
                currentExposure = exposure
                footerView.render(notice.modelShortName) { dismissNotice(notice) }
            }
            NativeInputFooterState(visible = notice != null)
        }.distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: HighUsageModelFooterView = footerView
            override val state: Flow<NativeInputFooterState> = state.onCompletion {
                // The widget can go away right after a prompt (the NTP hands off to a new chat), before the
                // updated context reaches this flow, so settle that prompt before ending the session.
                if (hostContext.value.promptSubmissions > (lastContext?.promptSubmissions ?: Int.MAX_VALUE)) measurement.promptSubmitted()
                measurement.inputSessionEnded()
            }

            override fun onDisplayed(displayed: Boolean) {
                isDisplayed = displayed
                if (displayed) currentExposure?.let(measurement::cardBecameVisible)
            }
        }
    }
}
