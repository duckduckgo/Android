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
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
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
    private val ctaResolver: UsageLimitCtaResolver,
    private val modelManager: DuckAiModelManager,
    private val subscriptions: Subscriptions,
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage>,
    private val duckChatFeature: DuckChatFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputFooterPlugin {

    override val priority: Int = 50

    private data class Inputs(
        val snapshot: UsageLimitsSnapshot?,
        val dismissals: Map<UsageWindow, UsageNoticeDismissal>,
        val actedOn: UsageNoticeActedOn?,
        val selectedModelId: String?,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter {
        val footerView = UsageLimitFooterView(context)
        var lastSelectedModelId: String? = null

        val surfaceActive = combine(hostContext, duckChatFeature.duckAiUsageWarnings().enabled()) { footerContext, enabled ->
            enabled && footerContext.isDuckAiSelected && !footerContext.isEditing && !footerContext.isFireMode
        }.distinctUntilChanged()

        val inputs = surfaceActive.flatMapLatest { active ->
            if (active) {
                combine(
                    repository.usageLimits(BrowserMode.REGULAR),
                    dismissalStore.dismissals,
                    dismissalStore.actedOn,
                    modelManager.modelState,
                ) { snapshot, dismissals, actedOn, modelState -> Inputs(snapshot, dismissals, actedOn, modelState.selectedModelId) }
            } else {
                flowOf(null)
            }
        }

        val state = combine(inputs, hostContext) { input, footerContext ->
            val previousModelId = lastSelectedModelId
            lastSelectedModelId = input?.selectedModelId ?: previousModelId
            val snapshot = input?.snapshot ?: return@combine NativeInputFooterState(visible = false)
            val notice = snapshot.notice
            if (UsageNoticeDismissalPolicy.isSuppressed(
                    notice,
                    input.dismissals[notice.window],
                )
            ) {
                return@combine NativeInputFooterState(visible = false)
            }
            if (input.actedOn?.applies(notice) == true) return@combine NativeInputFooterState(visible = false)
            if (!footerContext.isInputFocused) return@combine NativeInputFooterState(visible = false)

            val freeTrialEligible = snapshot.cta?.id == UsageCtaId.SUBSCRIBE &&
                runCatching { subscriptions.isFreeTrialEligible() }.getOrDefault(false)
            val resolvedCta = ctaResolver.resolve(snapshot.cta, modelManager.modelState.value, host.draft(), freeTrialEligible)
            retireIfManuallySwitched(snapshot, selectedModelId = input.selectedModelId, previousModelId = previousModelId)

            footerView.render(
                message = messageMapper.map(notice, currentTimeProvider.currentTimeMillis(), context.resources, resolvedCta),
                onDismiss = { appCoroutineScope.launch { dismissalStore.dismiss(notice) } },
                onCta = { resolvedCta?.let { runCta(it, snapshot, host) } },
            )
            NativeInputFooterState(visible = true, blocksComposer = notice.reached)
        }.distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: UsageLimitFooterView = footerView
            override val state: Flow<NativeInputFooterState> = state
        }
    }

    private fun runCta(
        cta: ResolvedUsageCta,
        snapshot: UsageLimitsSnapshot,
        host: NativeInputFooterHost,
    ) {
        when (cta) {
            is ResolvedUsageCta.SwitchModel -> {
                host.selectModel(cta.model.id)
                appCoroutineScope.launch { dismissalStore.markActedOn(snapshot.notice) }
            }
            is ResolvedUsageCta.StartUsingWeeklyLimit -> appCoroutineScope.launch {
                // The page hydrates these entries before its next request; they are written as supplied.
                val written = runCatching {
                    withContext(dispatchers.io()) {
                        val settings = storageProvider.forMode(BrowserMode.REGULAR).settings
                        cta.putEntries.forEach { entry -> settings.upsert(DuckAiBridgeSettingEntity(key = entry.key, value = entry.value)) }
                    }
                }
                // Without the entries the handoff would not take; leave the card up so the user can retry.
                if (written.isFailure) {
                    logcat(WARN) { "Duck.ai usage limits: weekly handoff write failed: ${written.exceptionOrNull()?.message}" }
                    return@launch
                }
                dismissalStore.markActedOn(snapshot.notice)
                withContext(dispatchers.main()) { host.startUsingWeeklyLimit() }
            }
            is ResolvedUsageCta.Subscribe -> host.openSubscriptionPurchase(USAGE_LIMIT_PURCHASE_ORIGIN)
        }
    }

    private fun retireIfManuallySwitched(
        snapshot: UsageLimitsSnapshot,
        selectedModelId: String?,
        previousModelId: String?,
    ) {
        if (previousModelId == null || selectedModelId == null || selectedModelId == previousModelId) return
        val cta = snapshot.cta?.takeIf { it.id == UsageCtaId.SWITCH_TO_CHEAPER || it.id == UsageCtaId.SWITCH_TO_FREE } ?: return
        if (selectedModelId !in ctaResolver.candidateIds(cta, previousModelId)) return
        appCoroutineScope.launch { dismissalStore.markActedOn(snapshot.notice) }
    }

    companion object {
        const val USAGE_LIMIT_PURCHASE_ORIGIN = "funnel_duckai_android__usagelimit"
    }
}
