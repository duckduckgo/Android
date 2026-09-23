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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputFooterPlugin::class,
    featureName = "pluginUsageLimitNativeInputFooter",
    parentFeatureName = "pluginPointNativeInputFooter",
)
class UsageLimitFooterPlugin @Inject constructor(
    private val repository: DuckAiUsageLimitsRepository,
    private val dismissalStore: UsageNoticeDismissalStore,
    private val messageMapper: UsageLimitFooterMessageMapper,
    private val duckChatFeature: DuckChatFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputFooterPlugin {

    override val priority: Int = 50

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
    ): NativeInputFooter {
        val footerView = UsageLimitFooterView(context)

        val surfaceActive = combine(hostContext, duckChatFeature.duckAiUsageWarnings().enabled()) { footerContext, enabled ->
            enabled && footerContext.isDuckAiSelected && !footerContext.isEditing && !footerContext.isFireMode
        }.distinctUntilChanged()

        val snapshotAndDismissal = surfaceActive.flatMapLatest { active ->
            if (active) {
                combine(repository.usageLimits(BrowserMode.REGULAR), dismissalStore.dismissal) { snapshot, dismissal -> snapshot to dismissal }
            } else {
                flowOf(null)
            }
        }

        val state = combine(snapshotAndDismissal, hostContext) { pair, footerContext ->
            val notice = pair?.first?.notice ?: return@combine NativeInputFooterState(visible = false)
            if (UsageNoticeDismissalPolicy.isSuppressed(notice, pair.second)) return@combine NativeInputFooterState(visible = false)
            if (!footerContext.isInputFocused) return@combine NativeInputFooterState(visible = false)

            footerView.render(messageMapper.map(notice, currentTimeProvider.currentTimeMillis(), context.resources)) {
                appCoroutineScope.launch { dismissalStore.dismiss(notice) }
            }
            NativeInputFooterState(visible = true, blocksComposer = notice.reached)
        }.distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: UsageLimitFooterView = footerView
            override val state: Flow<NativeInputFooterState> = state
        }
    }
}
