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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Step factories shared by more than one new-user onboarding flow, plus the helpers those steps need.
 * A flow-specific step lives with its flow's plan builder and moves here only once a second flow
 * actually uses it.
 */
internal class NewUserOnboardingSteps @Inject constructor(
    private val syncAutoRestore: SyncAutoRestore,
    private val appBuildConfig: AppBuildConfig,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val duckChat: DuckChat,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val onboardingPixelSender: OnboardingPixelSender,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun resolveFirstDialog(ctx: NewUserOnboardingPlanContext): FirstDialog =
        withContext(dispatchers.io()) {
            val canRestore = withTimeoutOrNull(BLOCK_STORE_TIMEOUT_MS) {
                try {
                    logcat { "Sync-AutoRestore: checking canRestore..." }
                    val result = syncAutoRestore.canRestore()
                    logcat(LogPriority.INFO) { "Sync-AutoRestore: canRestore=$result" }
                    result
                } catch (t: Throwable) {
                    coroutineContext.ensureActive()
                    logcat(LogPriority.WARN) { "Sync-AutoRestore: canRestore check failed - ${t.message}" }
                    false
                }
            } ?: false
            // Side-effecting (creates the DDG downloads dir, persists reinstall state) and must always run
            val isReinstall = appBuildConfig.isAppReinstall()
            ctx.isReinstall = isReinstall
            when {
                canRestore -> FirstDialog.SYNC_RESTORE
                isReinstall -> FirstDialog.REINSTALL
                else -> FirstDialog.INITIAL
            }
        }

    fun introAnimationStep(withDuckAi: Boolean = false) = NewUserOnboardingActivityStep(
        id = NewUserOnboardingStepIds.INTRO_ANIMATION,
        pixelName = null,
        resolveDialog = {
            NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi)
        },
        transition = { event ->
            when {
                event is NewUserOnboardingEvent.IntroAnimationFinished -> Advance
                else -> Stay
            }
        },
    )

    fun notificationPermissionStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_NOTIFICATIONS
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.NOTIFICATION_PERMISSION,
            pixelName = pixelName,
            resolveDialog = { NewUserOnboardingActivityDialog.NotificationPermission },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.NotificationPermissionFinished -> {
                        if (event.granted != null) {
                            onboardingPixelSender.fire(pixelName, OnboardingPixelAction.NotificationsConfirmed(granted = event.granted))
                        }
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    fun initialReinstallUserStep(
        firstDialog: SuspendMemo<FirstDialog>,
        quickSetupPlan: LinearOnboardingPlan,
        isCustomAiPlan: Boolean = false,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WELCOME
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INITIAL_REINSTALL_USER,
            pixelName = pixelName,
            precondition = {
                when (firstDialog()) {
                    FirstDialog.SYNC_RESTORE -> {
                        if (isCustomAiPlan) {
                            pixel.fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
                            true
                        } else {
                            false
                        }
                    }
                    FirstDialog.REINSTALL -> true
                    FirstDialog.INITIAL -> false
                }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.InitialReinstallUser },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.ContinueClicked -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    is NewUserOnboardingEvent.SkipRequested -> {
                        pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = false))
                        SwitchTo(quickSetupPlan)
                    }

                    else -> Stay
                }
            },
        )
    }

    fun initialStep(
        firstDialog: SuspendMemo<FirstDialog>,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WELCOME
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INITIAL,
            pixelName = pixelName,
            precondition = { firstDialog() == FirstDialog.INITIAL },
            resolveDialog = { NewUserOnboardingActivityDialog.Initial },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    fun comparisonChartStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SET_DEFAULT
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.COMPARISON_CHART,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.ComparisonChart },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        val showDefaultBrowserDialog = defaultRoleBrowserDialog.shouldShowDialog()
                        pixel.fire(
                            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
                            mapOf(PixelParameter.DEFAULT_BROWSER to (!showDefaultBrowserDialog).toString()),
                        )
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    fun defaultBrowserPromptStep() = NewUserOnboardingActivityStep(
        id = NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT,
        // No shown pixel of its own; the confirmed result belongs to the set-default pixel (started on the comparison-chart step).
        pixelName = null,
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },
        resolveDialog = { NewUserOnboardingActivityDialog.DefaultBrowserPrompt },
        transition = { event ->
            when {
                event is NewUserOnboardingEvent.DefaultBrowserPromptFinished -> {
                    onboardingPixelSender.fire(
                        OnboardingPixelName.ONBOARDING_SET_DEFAULT,
                        OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = event.isDefaultBrowser),
                    )
                    Advance
                }
                else -> Stay
            }
        },
    )

    fun addressBarPositionStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.ADDRESS_BAR_POSITION,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = isSplitOmnibarEnabled()) },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.AddressBarConfirmed -> {
                        val resolved = resolveOmnibarType(event.type)
                        settingsDataStore.omnibarType = resolved
                        fireAddressBarPositionPixel(resolved)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.AddressBarClicked(position = resolved))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    fun quickSetupStep(ctx: NewUserOnboardingPlanContext, forceWithAiInput: Boolean): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_QUICK_SETUP
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.QUICK_SETUP,
            pixelName = pixelName,
            resolveDialog = {
                val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                    defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
                }
                NewUserOnboardingActivityDialog.QuickSetup(
                    showSplitOption = isSplitOmnibarEnabled(),
                    hideSetDefaultBrowserRow = isDefault,
                    hideAddWidgetRow = hasWidget,
                    hideAddressBarRow = forceWithAiInput,
                    isReinstallUser = ctx.isReinstall,
                )
            },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.QuickSetupConfirmed -> {
                        val resolved = resolveOmnibarType(event.type)
                        settingsDataStore.omnibarType = resolved
                        applyInputModeSelection(ctx, event.withAi, fireTelemetry = false)
                        if (forceWithAiInput) {
                            duckChat.setInputScreenUserSetting(true)
                        }
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.QuickSetupClicked(
                                addressBarPosition = resolved,
                                inputScreenSelected = event.withAi,
                            ),
                        )
                        AbortPlan
                    }
                    else -> Stay
                }
            },
        )
    }

    suspend fun applyInputModeSelection(
        ctx: NewUserOnboardingPlanContext,
        withAi: Boolean,
        fireTelemetry: Boolean,
    ) {
        if (fireTelemetry) {
            if (withAi) {
                pixel.fire(PREONBOARDING_AICHAT_SELECTED)
                inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = ctx.isReinstall)
            } else {
                pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
            }
        }
        duckChat.setCosmeticInputScreenUserSetting(withAi)
        onboardingStore.storeInputScreenSelection(withAi)
    }

    private fun fireAddressBarPositionPixel(resolved: OmnibarType) {
        when (resolved) {
            OmnibarType.SINGLE_BOTTOM -> pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SPLIT -> pixel.fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SINGLE_TOP -> Unit
        }
    }

    private suspend fun resolveOmnibarType(selected: OmnibarType): OmnibarType =
        if (selected == OmnibarType.SPLIT && !isSplitOmnibarEnabled()) OmnibarType.SINGLE_TOP else selected

    private suspend fun isSplitOmnibarEnabled(): Boolean =
        withContext(dispatchers.io()) {
            androidBrowserConfigFeature.splitOmnibar().isEnabled() &&
                androidBrowserConfigFeature.splitOmnibarWelcomePage().isEnabled()
        }

    companion object {
        private const val BLOCK_STORE_TIMEOUT_MS = 3_000L
    }
}

/** Which dialog a plan opens with, resolved once per run by [NewUserOnboardingSteps.resolveFirstDialog]. */
internal enum class FirstDialog { SYNC_RESTORE, REINSTALL, INITIAL }
