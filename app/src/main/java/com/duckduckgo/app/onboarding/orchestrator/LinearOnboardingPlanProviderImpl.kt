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

import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingPlanProvider
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Return
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LinearOnboardingPlanProviderImpl @Inject constructor(
    private val onboardingStore: OnboardingStore,
    private val duckChat: DuckChat,
    private val appBuildConfig: AppBuildConfig,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
) : LinearOnboardingPlanProvider {

    // Cross-step context kept private to the planner. Steps close over it via
    // transitions / resolveAction. Typed data class beats a scattering of @Volatile fields.
    private val context = MutableStateFlow(OnboardingPocContext())

    // Side plan reached via SwitchTo from initial_reinstall_user.
    private val skipPlan: LinearOnboardingPlan by lazy {
        LinearOnboardingPlan(steps = listOf(skipOnboardingOptionStep()))
    }

    override suspend fun buildMainPlan(): LinearOnboardingPlan = LinearOnboardingPlan(
        steps = listOf(
            introAnimationStep(),
            initialReinstallUserStep(),
            initialStep(),
            inputScreenPreviewStep(),
            duckAiStep(),
            comparisonChartStep(),
            defaultBrowserPromptStep(),
            addressBarPositionStep(),
        ),
    )

    private fun introAnimationStep() = OnboardingActivityStep(
        id = "intro_animation",
        resolveDialog = { OnboardingActivityDialog.IntroAnimation },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            if (event is OnboardingEvent.PrimaryClicked) Advance else Stay
        },
    )

    // Mutually exclusive with initialStep via precondition on reinstall status.
    // Secondary enters the skip side plan via SwitchTo; Return from the side plan
    // resumes the main plan past this step (so the user lands on the next eligible
    // step, e.g. input_screen_preview).
    private fun initialReinstallUserStep() = OnboardingActivityStep(
        id = "initial_reinstall_user",
        precondition = { appBuildConfig.isAppReinstall() },
        resolveDialog = { OnboardingActivityDialog.InitialReinstallUser },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            when (event) {
                is OnboardingEvent.PrimaryClicked -> Advance
                is OnboardingEvent.SecondaryClicked -> {
                    pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                    SwitchTo(skipPlan)
                }
                else -> Stay
            }
        },
    )

    private fun initialStep() = OnboardingActivityStep(
        id = "initial",
        precondition = { !appBuildConfig.isAppReinstall() },
        resolveDialog = { OnboardingActivityDialog.Initial },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            if (event is OnboardingEvent.PrimaryClicked) Advance else Stay
        },
    )

    private fun inputScreenPreviewStep() = OnboardingActivityStep(
        id = "input_screen_preview",
        // PoC: hardcoded — no experiment enrolment, no isSearchDefault variation.
        resolveDialog = { OnboardingActivityDialog.InputScreenPreview(isSearchDefault = false) },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            when (event) {
                is OnboardingEvent.DuckAiPromptSubmitted -> {
                    context.update { it.copy(pendingDuckAiPrompt = event.prompt) }
                    onboardingStore.setDuckAiOnboardingFlow()
                    Advance
                }
                else -> Stay
            }
        },
    )

    private fun duckAiStep() = BrowserActivityStep(
        id = "duck_ai",
        resolveAction = {
            val prompt = context.value.pendingDuckAiPrompt
                ?: error("duck_ai step requires a pending prompt set by the preceding step")
            val url = duckChat.getDuckChatUrl(prompt, autoPrompt = true) + "&flow=mobile-app-onboarding"
            BrowserActivityAction.OpenDuckChat(url)
        },
        transition = { event ->
            if (event is OnboardingEvent.DuckAiFireCompleted) Advance else Stay
        },
    )

    private fun comparisonChartStep() = OnboardingActivityStep(
        id = "comparison_chart",
        resolveDialog = { OnboardingActivityDialog.ComparisonChart },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            if (event is OnboardingEvent.PrimaryClicked) Advance else Stay
        },
    )

    // Distinct step for the OS default-browser prompt — skipped entirely when
    // shouldShowDialog() is false. The VM/fragment side-effects the prompt when
    // it becomes current; we advance once DefaultBrowserPromptFinished arrives.
    private fun defaultBrowserPromptStep() = OnboardingActivityStep(
        id = "default_browser_prompt",
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },
        resolveDialog = { OnboardingActivityDialog.DefaultBrowserPrompt },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            if (event is OnboardingEvent.DefaultBrowserPromptFinished) Advance else Stay
        },
    )

    private fun addressBarPositionStep() = OnboardingActivityStep(
        id = "address_bar_position",
        // PoC: hardcoded showSplitOption=true.
        resolveDialog = { OnboardingActivityDialog.AddressBarPosition(showSplitOption = true) },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            when (event) {
                is OnboardingEvent.OmnibarTypeSelected -> {
                    settingsDataStore.omnibarType = event.type
                    // Selection alone doesn't advance; PrimaryClicked does.
                    Stay
                }
                is OnboardingEvent.PrimaryClicked -> Advance
                else -> Stay
            }
        },
    )

    // Side plan step. Primary terminates the whole linear flow (AbortPlan); Secondary
    // pops back to the caller plan via Return, which resumes past the step that did
    // SwitchTo (i.e. past initial_reinstall_user).
    private fun skipOnboardingOptionStep() = OnboardingActivityStep(
        id = "skip_onboarding_option",
        resolveDialog = { OnboardingActivityDialog.SkipOnboardingOption },
        transition = { event ->
            if (event.isDevSettingSkipEvent()) return@OnboardingActivityStep AbortPlan
            when (event) {
                is OnboardingEvent.PrimaryClicked -> {
                    pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                    AbortPlan
                }
                is OnboardingEvent.SecondaryClicked -> {
                    pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                    Return
                }
                else -> Stay
            }
        },
    )
}

private data class OnboardingPocContext(
    val pendingDuckAiPrompt: String? = null,
)

private fun LinearOnboardingEvent.isDevSettingSkipEvent(): Boolean = this is OnboardingEvent.SkipOnboardingDevOptionClicked
