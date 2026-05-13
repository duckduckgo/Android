/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.app.onboarding.orchestrator.OnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.OnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.OnboardingEvent
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN_PREVIEW
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class BrandDesignUpdatePageViewModel @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val onboardingStore: OnboardingStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val orchestrator: LinearOnboardingOrchestrator,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val appInstallStore: AppInstallStore,
) : ViewModel() {

    data class ViewState(
        val hasPlayedIntroAnimation: Boolean = false,
        val hasAnimatedCurrentDialog: Boolean = false,
        val currentDialog: PreOnboardingDialogType? = null,
        val selectedAddressBarPosition: OmnibarType = OmnibarType.SINGLE_TOP,
        val inputScreenSelected: Boolean = true,
        val showSplitOption: Boolean = false,
        val isReinstallUser: Boolean = false,
        val inputScreenPreviewSearchSuggestions: List<DaxDialogIntroOption> = emptyList(),
        val inputScreenPreviewChatSuggestions: List<DaxDialogIntroOption> = emptyList(),
        val inputScreenPreviewIsSearchSelected: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var maxPageCount: Int = 2

    init {
        viewModelScope.launch(dispatchers.io()) {
            maxPageCount = if (androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()) 3 else 2
        }
        seedIntroAnimationFlagFromOrchestrator()
        observeOrchestrator()
    }

    // The fragment-scoped VM is recreated on every OnboardingActivity launch. Without
    // this seed, a re-entry mid-flow (e.g. after the BrowserActivity duck_ai step) would
    // briefly expose hasPlayedIntroAnimation=false to the fragment — which would replay
    // the intro animation before observeOrchestrator's async collector corrects state.
    // Read orchestrator.state.value synchronously here so the initial viewState already
    // reflects reality by the time the fragment subscribes.
    private fun seedIntroAnimationFlagFromOrchestrator() {
        val pastIntroAnimation = when (val state = orchestrator.state.value) {
            is LinearOnboardingState.InProgress -> state.currentStep.id != STEP_INTRO_ANIMATION
            LinearOnboardingState.Completed,
            LinearOnboardingState.Skipped,
            -> true
            LinearOnboardingState.NotStarted -> false
        }
        if (pastIntroAnimation) {
            _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
        }
    }

    sealed interface Command {
        data object RequestNotificationPermissions : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object Finish : Command
        data class FinishAndSubmitSearchQuery(val query: String) : Command
        data class FinishAndSubmitChatPrompt(val prompt: String) : Command
        data object OnboardingSkipped : Command
        data object SkipDialogAnimation : Command
    }

    private fun observeOrchestrator() {
        orchestrator.state
            .onEach { state ->
                when (state) {
                    LinearOnboardingState.NotStarted -> Unit
                    is LinearOnboardingState.InProgress -> {
                        val step = state.currentStep as? OnboardingActivityStep ?: return@onEach
                        applyDialog(step.resolveDialog())
                    }
                    LinearOnboardingState.Completed -> _commands.send(Command.Finish)
                    LinearOnboardingState.Skipped -> _commands.send(Command.OnboardingSkipped)
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun applyDialog(dialog: OnboardingActivityDialog) {
        when (dialog) {
            OnboardingActivityDialog.IntroAnimation -> {
                // Fragment renders the intro animation based on hasPlayedIntroAnimation;
                // do not overwrite that field — the fragment owns animation progress.
                _viewState.update { it.copy(currentDialog = null) }
            }
            OnboardingActivityDialog.InitialReinstallUser -> {
                _viewState.update {
                    it.copy(
                        currentDialog = INITIAL_REINSTALL_USER,
                        hasAnimatedCurrentDialog = false,
                        isReinstallUser = true,
                    )
                }
                pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            }
            OnboardingActivityDialog.Initial -> {
                _viewState.update {
                    it.copy(
                        currentDialog = INITIAL,
                        hasAnimatedCurrentDialog = false,
                        isReinstallUser = false,
                    )
                }
                pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            }
            is OnboardingActivityDialog.InputScreenPreview -> {
                _viewState.update {
                    it.copy(
                        currentDialog = INPUT_SCREEN_PREVIEW,
                        hasAnimatedCurrentDialog = false,
                        inputScreenPreviewSearchSuggestions = onboardingStore.getSearchOptions(),
                        inputScreenPreviewChatSuggestions = onboardingStore.getChatSuggestions(),
                        inputScreenPreviewIsSearchSelected = dialog.isSearchDefault,
                    )
                }
            }
            OnboardingActivityDialog.ComparisonChart -> {
                _viewState.update {
                    it.copy(
                        currentDialog = COMPARISON_CHART,
                        hasAnimatedCurrentDialog = false,
                    )
                }
                pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            }
            OnboardingActivityDialog.DefaultBrowserPrompt -> {
                // This "dialog" is an OS-level system intent, not a Dax surface. Build the
                // intent and ask the fragment to launch it. If we can't build one, synthesise
                // a "finished" event so the orchestrator can advance past the step.
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                } else {
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                    viewModelScope.launch {
                        // poc: hardcoding isDefaultBrowser = false
                        orchestrator.onEvent(OnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
                    }
                }
            }
            is OnboardingActivityDialog.AddressBarPosition -> {
                _viewState.update {
                    it.copy(
                        currentDialog = ADDRESS_BAR_POSITION,
                        hasAnimatedCurrentDialog = false,
                        showSplitOption = dialog.showSplitOption,
                    )
                }
            }
            OnboardingActivityDialog.SkipOnboardingOption -> {
                _viewState.update {
                    it.copy(
                        currentDialog = SKIP_ONBOARDING_OPTION,
                        hasAnimatedCurrentDialog = false,
                    )
                }
                pixel.fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
            }
        }
    }

    fun onDialogTapped() = skipDialogAnimations()
    fun onBackgroundTapped() = skipDialogAnimations()

    fun onDialogAnimationStarted() {
        _viewState.update { it.copy(hasAnimatedCurrentDialog = true) }
    }

    fun onIntroAnimationFinished() {
        _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
        viewModelScope.launch {
            delay(2.seconds)
            _commands.send(Command.RequestNotificationPermissions)
        }
    }

    /**
     * Called by the fragment after the notification-permission flow settles to advance
     * past intro_animation. Idempotent: only fires when we are still on intro_animation.
     */
    fun loadDaxDialog() {
        viewModelScope.launch {
            val current = orchestrator.state.value as? LinearOnboardingState.InProgress ?: return@launch
            if (current.currentStep.id == STEP_INTRO_ANIMATION) {
                orchestrator.onEvent(OnboardingEvent.PrimaryClicked)
            }
        }
    }

    fun onPrimaryCtaClicked() {
        viewModelScope.launch { orchestrator.onEvent(OnboardingEvent.PrimaryClicked) }
    }

    fun onSecondaryCtaClicked() {
        viewModelScope.launch { orchestrator.onEvent(OnboardingEvent.SecondaryClicked) }
    }

    fun onInputModeDemoQuerySubmitted(query: String, isChat: Boolean) {
        viewModelScope.launch {
            if (isChat) {
                orchestrator.onEvent(OnboardingEvent.DuckAiPromptSubmitted(query))
            } else {
                // PoC plan covers only the chat path.
                // _commands.send(Command.FinishAndSubmitSearchQuery(query))
            }
        }
    }

    fun onDefaultBrowserSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = true
        pixel.fire(
            AppPixelName.DEFAULT_BROWSER_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
        viewModelScope.launch {
            orchestrator.onEvent(OnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
        }
    }

    fun onDefaultBrowserNotSet() {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = false
        pixel.fire(
            AppPixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
        viewModelScope.launch {
            orchestrator.onEvent(OnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        }
    }

    fun onAddressBarPositionOptionSelected(selectedOption: OmnibarType) {
        _viewState.update { it.copy(selectedAddressBarPosition = selectedOption) }
        viewModelScope.launch {
            orchestrator.onEvent(OnboardingEvent.OmnibarTypeSelected(selectedOption))
        }
    }

    fun onInputScreenOptionSelected(withAi: Boolean) {
        _viewState.update { it.copy(inputScreenSelected = withAi) }
    }

    fun notificationRuntimePermissionRequested() {
        pixel.fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
    }

    fun notificationRuntimePermissionGranted() {
        pixel.fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    fun getMaxPageCount(): Int = maxPageCount

    private fun skipDialogAnimations() {
        viewModelScope.launch { _commands.send(Command.SkipDialogAnimation) }
    }

    companion object {
        private const val STEP_INTRO_ANIMATION = "intro_animation"
    }
}
