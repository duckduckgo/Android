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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.codegen.annotations.ContributesActivePlugin
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.models.forTab
import com.duckduckgo.duckchat.impl.nativeinput.footer.FooterCategory
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooter
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterPlugin
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterState
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningCta
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningExposure
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings.UsageWarningMeasurements
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
    private val nativeInputStateProvider: NativeInputStateProvider,
    @RegularMode private val chatStore: DuckAiChatStore,
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage>,
    private val duckChatFeature: DuckChatFeature,
    private val currentTimeProvider: CurrentTimeProvider,
    private val measurements: UsageWarningMeasurements,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputFooterPlugin {

    override val priority: Int = 50
    override val category: FooterCategory = FooterCategory.USAGE_NOTICE

    private data class Inputs(
        val snapshot: UsageLimitsSnapshot?,
        val dismissals: Map<UsageWindow, UsageNoticeDismissal>,
        val actedOn: UsageNoticeActedOn?,
        val modelState: ModelState,
        val isNewChat: Boolean,
        val pickedModelId: String?,
        val chatModelId: String?,
    ) {
        val selectedModelId: String? get() = modelState.selectedModelId

        /**
         * The model the user switched away from since [previous], or null when nothing changed or the app moved it.
         * While `resetModelForNewChats` is on, the manager moves the global selection itself (fetch, over-limit default,
         * reset), and a user's pick lands on the tab instead, so only a new chat's own pick counts as a switch.
         */
        fun switchedFrom(previous: Inputs?): String? {
            val previousModelId = previous?.selectedModelId ?: return null
            if (selectedModelId == null || selectedModelId == previousModelId) return null
            if (modelState.newChatDefault == null) return previousModelId
            val isOwnPick = isNewChat && previous.isNewChat && pickedModelId != null && pickedModelId != previous.pickedModelId
            return previousModelId.takeIf { isOwnPick }
        }

        /** The model the active chat sends on: a new chat's selection, or the model an existing chat was saved with. */
        private val activeModelId: String?
            get() = if (isNewChat) selectedModelId else chatModelId

        /**
         * Web names an over-limit default only while free models are still usable, and web offers no switch away from
         * one, so a reached notice on a chat already on a usable model would block it with no way out. Only applies
         * while `resetModelForNewChats` is on.
         */
        val isOnModelAllowedOverLimit: Boolean
            get() {
                val overLimitModelId = snapshot?.newChatDefault?.modelId ?: return false
                if (modelState.newChatDefault == null) return false
                val model = modelState.models.firstOrNull { it.id == activeModelId } ?: return false
                return model.id == overLimitModelId || model.requiredTier == UserTier.FREE
            }

        /**
         * What the CTA targets are looked up against. While `resetModelForNewChats` is on, the global selection is the
         * new-chat default, not the model an existing chat is on, so an existing chat uses the page's own targets,
         * which web keys to the chat it shows.
         */
        val ctaModelState: ModelState
            get() = if (!isNewChat && modelState.newChatDefault != null) modelState.copy(selectedModelId = null) else modelState
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun createFooter(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): NativeInputFooter {
        val footerView = UsageLimitFooterView(context)
        var lastInputs: Inputs? = null
        val measurement = measurements.forHost(host) { DuckChatPixelSurface.from(hostContext.value.inputContext) }
        var lastContext: NativeInputFooterContext? = null
        var currentExposure: UsageWarningExposure? = null
        var isDisplayed = false

        val activeBrowserMode = combine(hostContext, duckChatFeature.duckAiUsageWarnings().enabled()) { footerContext, enabled ->
            footerContext.browserMode.takeIf {
                enabled && footerContext.isDuckAiSelected && !footerContext.isEditing && it != BrowserMode.FIRE
            }
        }.distinctUntilChanged()

        // Starts with no chats so the footer is not held back while the store loads.
        val chats = chatStore.getChatsFlow().map<List<DuckAiChat>, List<DuckAiChat>?> { it }.onStart { emit(null) }
        val activeChat = combine(nativeInputStateProvider.state, chats) { inputState, chats ->
            inputState to inputState.chatId?.let { chatId -> chats?.firstOrNull { it.chatId == chatId }?.model }
        }.distinctUntilChanged()

        val inputs = activeBrowserMode.flatMapLatest { browserMode ->
            if (browserMode != null) {
                combine(
                    repository.usageLimits(browserMode),
                    dismissalStore.dismissals,
                    dismissalStore.actedOn,
                    modelManager.modelState,
                    activeChat,
                ) { snapshot, dismissals, actedOn, modelState, (inputState, chatModelId) ->
                    Inputs(
                        snapshot,
                        dismissals,
                        actedOn,
                        modelState.forTab(inputState),
                        isNewChat = inputState.chatId == null,
                        pickedModelId = inputState.newChatModelId,
                        chatModelId = chatModelId,
                    )
                }
            } else {
                flowOf(null)
            }
        }

        val state = combine(inputs, hostContext) { input, footerContext ->
            val switchedFromModelId = input?.switchedFrom(lastInputs)
            if (input?.selectedModelId != null) lastInputs = input
            val previousContext = lastContext
            lastContext = footerContext
            // A prompt is attributed before the focus loss that follows it, even when both arrive in one emission.
            if (previousContext != null && footerContext.promptSubmissions > previousContext.promptSubmissions) measurement.promptSubmitted()
            if (switchedFromModelId != null) measurement.modelSwitched()
            if (input == null || !footerContext.isInputFocused) measurement.inputSessionEnded()
            val snapshot = input?.snapshot ?: return@combine NativeInputFooterState(visible = false)
            val notice = snapshot.notice
            if (notice.resetsAtMillis <= currentTimeProvider.currentTimeMillis()) return@combine NativeInputFooterState(visible = false)
            if (UsageNoticeDismissalPolicy.isSuppressed(
                    notice,
                    input.dismissals[notice.window],
                )
            ) {
                return@combine NativeInputFooterState(visible = false)
            }
            if (input.actedOn?.applies(notice) == true || input.isOnModelAllowedOverLimit) return@combine NativeInputFooterState(visible = false)
            if (!footerContext.isInputFocused) return@combine NativeInputFooterState(visible = false)

            val resolvedCta = ctaResolver.resolve(snapshot.cta, input.ctaModelState, host.draft(), input.modelState.isFreeTrialEligible)
            retireIfManuallySwitched(snapshot, selectedModelId = input.selectedModelId, previousModelId = switchedFromModelId)

            val exposure = UsageWarningExposure.of(notice)
            if (isDisplayed && exposure != currentExposure) measurement.cardBecameVisible(exposure)
            currentExposure = exposure

            footerView.render(
                message = messageMapper.map(notice, currentTimeProvider.currentTimeMillis(), context.resources, resolvedCta),
                onDismiss = {
                    measurement.warningDismissed()
                    appCoroutineScope.launch { dismissalStore.dismiss(notice) }
                },
                onCta = {
                    resolvedCta?.let {
                        measurement.ctaTapped(it.measurementCta())
                        runCta(it, snapshot, host, footerContext.browserMode)
                    }
                },
            )
            NativeInputFooterState(visible = true, blocksComposer = notice.reached)
        }.distinctUntilChanged()

        return object : NativeInputFooter {
            override val view: UsageLimitFooterView = footerView
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

    private fun ResolvedUsageCta.measurementCta(): UsageWarningCta = when (this) {
        is ResolvedUsageCta.SwitchModel -> UsageWarningCta.SWITCH_MODEL
        is ResolvedUsageCta.StartUsingWeeklyLimit -> UsageWarningCta.WEEKLY_LIMIT
        is ResolvedUsageCta.Subscribe -> UsageWarningCta.UPSELL
    }

    private fun runCta(
        cta: ResolvedUsageCta,
        snapshot: UsageLimitsSnapshot,
        host: NativeInputFooterHost,
        browserMode: BrowserMode,
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
                        val settings = storageProvider.forMode(browserMode).settings
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
