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
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanBootstrapper
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingResult
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingStepIds
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.orchestrator.stepIndicatorProgress
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.*
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.AI_COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.COMPARISON_CHART
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INITIAL_REINSTALL_USER
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.INPUT_SCREEN
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SKIP_ONBOARDING_OPTION
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.SYNC_RESTORE
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.forPlan
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class BrandDesignUpdatePageViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val dispatchers: DispatcherProvider,
    private val onboardingStore: OnboardingStore,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val orchestrator: LinearOnboardingOrchestrator,
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
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
        val hideSetDefaultBrowserRow: Boolean = false,
        val hideAddWidgetRow: Boolean = false,
        val isCustomAiOnboardingFlow: Boolean = false,
        val stepIndicator: StepProgress? = null,
        val onboardingImprovementsV2Enabled: Boolean = true,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var quickSetupDefaultBrowserDialogShown: Boolean = false

    private var notificationPermissionFlowStarted = false

    private var notificationPermissionGranted: Boolean? = null

    init {
        start()
    }

    sealed interface Command {
        data object RequestNotificationPermissions : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object Finish : Command
        data class FinishAndSubmitSearchQuery(val query: String) : Command
        data class FinishAndSubmitChatPrompt(val prompt: String) : Command
        data object OnboardingSkipped : Command
        data object SkipDialogAnimation : Command
        data class ShowQuickSetupAddressBarPositionBottomSheet(
            val initialSelection: OmnibarType,
            val showSplitOption: Boolean,
        ) : Command
        data class ShowQuickSetupSearchOptionsBottomSheet(val initialWithAi: Boolean) : Command
        data class ShowQuickSetupDefaultBrowserDialog(val intent: Intent) : Command
        data object OpenDefaultBrowserSystemSettings : Command
        data object LaunchAddWidgetPrompt : Command
        data object ShowRemoveWidgetBottomSheet : Command
        data class SyncAddWidgetSwitch(val isChecked: Boolean) : Command
        data class SyncQuickSetupSwitches(
            val defaultBrowserChecked: Boolean,
            val widgetChecked: Boolean,
        ) : Command
        data class PlayIntroAnimation(val withDuckAi: Boolean = false) : Command
        data object HandOffToBrowserActivity : Command
    }

    fun onDialogTapped() {
        skipDialogAnimations()
    }

    fun onBackgroundTapped() {
        skipDialogAnimations()
    }

    fun onDialogAnimationStarted() {
        _viewState.update { it.copy(hasAnimatedCurrentDialog = true) }
    }

    private fun setCurrentDialog(
        dialogType: PreOnboardingDialogType,
        stepIndicator: StepProgress? = null,
    ) {
        _viewState.update {
            it.copy(
                currentDialog = dialogType,
                hasAnimatedCurrentDialog = false,
                stepIndicator = stepIndicator,
            )
        }
        fireDialogShownPixel(dialogType)
    }

    private fun setInputScreenPreviewDialog(
        isSearchDefault: Boolean,
        stepIndicator: StepProgress?,
    ) {
        _viewState.update {
            it.copy(
                currentDialog = INPUT_SCREEN_PREVIEW,
                hasAnimatedCurrentDialog = false,
                inputScreenPreviewSearchSuggestions = onboardingStore.getSearchOptions(),
                inputScreenPreviewChatSuggestions = onboardingStore.getChatSuggestions(),
                inputScreenPreviewIsSearchSelected = isSearchDefault,
                stepIndicator = stepIndicator,
            )
        }
        fireDialogShownPixel(INPUT_SCREEN_PREVIEW)
    }

    private fun fireDialogShownPixel(dialogType: PreOnboardingDialogType) {
        when (dialogType) {
            SYNC_RESTORE -> pixel.fire(PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
            INITIAL_REINSTALL_USER -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            INITIAL -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            COMPARISON_CHART -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            AI_COMPARISON_CHART -> pixel.fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
            ADDRESS_BAR_POSITION -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            INPUT_SCREEN -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
            INPUT_SCREEN_PREVIEW, QUICK_SETUP, SKIP_ONBOARDING_OPTION -> Unit
        }
        viewModelScope.launch { orchestrator.onEvent(NewUserOnboardingEvent.Presented) }
    }

    fun onIntroAnimationStarted() {
        _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
    }

    fun onIntroAnimationFinished() = emit(NewUserOnboardingEvent.IntroAnimationFinished)

    fun notificationPermissionFlowFinished() = emit(NewUserOnboardingEvent.NotificationPermissionFinished(granted = notificationPermissionGranted))

    fun onPrimaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            SYNC_RESTORE -> emit(NewUserOnboardingEvent.RestoreRequested)
            INITIAL, INITIAL_REINSTALL_USER, COMPARISON_CHART, AI_COMPARISON_CHART, INPUT_SCREEN_PREVIEW ->
                emit(NewUserOnboardingEvent.ContinueClicked)
            ADDRESS_BAR_POSITION ->
                emit(NewUserOnboardingEvent.AddressBarConfirmed(_viewState.value.selectedAddressBarPosition))
            INPUT_SCREEN ->
                emit(NewUserOnboardingEvent.InputModeConfirmed(_viewState.value.inputScreenSelected))
            QUICK_SETUP -> {
                val state = _viewState.value
                emit(NewUserOnboardingEvent.QuickSetupConfirmed(state.selectedAddressBarPosition, state.inputScreenSelected))
            }
            SKIP_ONBOARDING_OPTION -> Unit
        }
    }

    fun onSecondaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            INITIAL_REINSTALL_USER, SYNC_RESTORE -> emit(NewUserOnboardingEvent.SkipRequested)
            INITIAL,
            COMPARISON_CHART,
            AI_COMPARISON_CHART,
            ADDRESS_BAR_POSITION,
            INPUT_SCREEN,
            INPUT_SCREEN_PREVIEW,
            QUICK_SETUP,
            SKIP_ONBOARDING_OPTION,
            -> Unit
        }
    }

    fun onInputModeDemoQuerySubmitted(
        query: String,
        isChat: Boolean,
        fromSuggestion: Boolean,
    ) {
        emit(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = query, isChat = isChat, fromSuggestion = fromSuggestion))
    }

    fun onDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true)
        emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
    }

    fun onDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false)
        emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
    }

    fun onQuickSetupDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true, fireTelemetry = false)
    }

    fun onQuickSetupDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false, fireTelemetry = false)
    }

    private fun recordDefaultBrowserDialogResult(
        isSet: Boolean,
        fireTelemetry: Boolean = true,
    ) {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = isSet
        if (fireTelemetry) {
            fireDefaultBrowserResultTelemetry(isSet)
        }
    }

    private fun fireDefaultBrowserResultTelemetry(isSet: Boolean) {
        val pixelName = if (isSet) AppPixelName.DEFAULT_BROWSER_SET else AppPixelName.DEFAULT_BROWSER_NOT_SET
        pixel.fire(pixelName, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
    }

    fun onAddressBarPositionOptionSelected(selectedOption: OmnibarType) {
        _viewState.update { it.copy(selectedAddressBarPosition = selectedOption) }
    }

    fun onInputScreenOptionSelected(withAi: Boolean) {
        _viewState.update { it.copy(inputScreenSelected = withAi) }
    }

    fun onQuickSetupAddressBarPositionEditClicked() {
        val state = _viewState.value
        viewModelScope.launch {
            _commands.send(
                Command.ShowQuickSetupAddressBarPositionBottomSheet(
                    initialSelection = state.selectedAddressBarPosition,
                    showSplitOption = state.showSplitOption,
                ),
            )
        }
    }

    fun onQuickSetupSearchOptionsEditClicked() {
        viewModelScope.launch {
            _commands.send(Command.ShowQuickSetupSearchOptionsBottomSheet(initialWithAi = _viewState.value.inputScreenSelected))
        }
    }

    fun onQuickSetupSetAsDefaultClicked() {
        viewModelScope.launch {
            if (!quickSetupDefaultBrowserDialogShown) {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    quickSetupDefaultBrowserDialogShown = true
                    _commands.send(Command.ShowQuickSetupDefaultBrowserDialog(intent))
                    return@launch
                }
            }
            _commands.send(Command.OpenDefaultBrowserSystemSettings)
        }
    }

    fun onQuickSetupSetAsDefaultUnchecked() {
        viewModelScope.launch {
            _commands.send(Command.OpenDefaultBrowserSystemSettings)
        }
    }

    fun checkQuickSetupSwitchesState() {
        if (_viewState.value.currentDialog == QUICK_SETUP) {
            viewModelScope.launch {
                val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                    defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
                }
                _commands.send(
                    Command.SyncQuickSetupSwitches(
                        defaultBrowserChecked = isDefault,
                        widgetChecked = hasWidget,
                    ),
                )
            }
        }
    }

    fun onQuickSetupAddHomescreenWidgetClicked() {
        viewModelScope.launch {
            _commands.send(Command.LaunchAddWidgetPrompt)
        }
    }

    fun onQuickSetupRemoveHomescreenWidgetClicked() {
        viewModelScope.launch {
            _commands.send(Command.ShowRemoveWidgetBottomSheet)
        }
    }

    fun checkWidgetAddedState() {
        viewModelScope.launch {
            val hasWidget = withContext(dispatchers.io()) { widgetCapabilities.hasInstalledWidgets }
            _commands.send(Command.SyncAddWidgetSwitch(isChecked = hasWidget))
        }
    }

    fun notificationRuntimePermissionRequested() {
        pixel.fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
        viewModelScope.launch { orchestrator.onEvent(NewUserOnboardingEvent.Presented) }
    }

    fun notificationRuntimePermissionGranted() {
        pixel.fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
        notificationPermissionGranted = true
    }

    fun notificationRuntimePermissionDenied() {
        notificationPermissionGranted = false
    }

    private suspend fun isOnboardingImprovementsV2Enabled(): Boolean =
        withContext(dispatchers.io()) {
            onboardingBrandDesignUpdateToggles.onboardingImprovementsV2().isEnabled()
        }

    private fun skipDialogAnimations() {
        viewModelScope.launch {
            _commands.send(Command.SkipDialogAnimation)
        }
    }

    private fun start() {
        seedHasPlayedIntroAnimation()
        viewModelScope.launch {
            if (orchestrator.state.value is LinearOnboardingState.NotStarted) {
                // Safeguard in case OnboardingActivity is restored after process death and does
                // not route through LaunchViewModel; restart the plan so onboarding resumes from the top.
                newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()
            }
            // Resolve the custom AI signal before collecting orchestrator state, so the flag is set
            // before any dialog is applied.
            _viewState.update { it.copy(isCustomAiOnboardingFlow = customAiOnboardingStore.isEnabled()) }
            _viewState.update { it.copy(onboardingImprovementsV2Enabled = isOnboardingImprovementsV2Enabled()) }
            observeOrchestratorState()
        }
    }

    // The fragment-scoped VM is recreated on every OnboardingActivity entry. Seed past-intro state
    // synchronously so a mid-flow re-entry does not replay the intro before the collector catches up.
    private fun seedHasPlayedIntroAnimation() {
        val isPastIntro = when (val state = orchestrator.state.value) {
            is LinearOnboardingState.InProgress ->
                (state.currentStep as? NewUserOnboardingActivityStep)?.id != NewUserOnboardingStepIds.INTRO_ANIMATION
            is LinearOnboardingState.Completed, is LinearOnboardingState.Skipped -> true
            LinearOnboardingState.NotStarted -> false
        }
        if (isPastIntro) {
            _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
        }
    }

    private fun observeOrchestratorState() {
        orchestrator.state
            .forPlan(NewUserOnboardingPlanProvider.ROOT_PLAN_ID)
            .onEach { state ->
                when (state) {
                    is LinearOnboardingState.InProgress -> {
                        val step = state.currentStep
                        when (step.host) {
                            LinearOnboardingHost.OnboardingActivity -> {
                                // stay
                            }
                            LinearOnboardingHost.BrowserActivity -> {
                                _commands.send(Command.HandOffToBrowserActivity)
                                return@onEach
                            }
                            else -> {
                                // Any other host is not this VM's screen (this VM only drives the new-user onboarding plan); ignore.
                                return@onEach
                            }
                        }
                        if (step is NewUserOnboardingActivityStep) {
                            applyDialog(step.resolveDialog(), state.stepIndicatorProgress())
                        }
                    }
                    is LinearOnboardingState.Completed -> {
                        when (val result = state.result as? NewUserOnboardingResult) {
                            is NewUserOnboardingResult.LaunchChat -> _commands.send(Command.FinishAndSubmitChatPrompt(prompt = result.prompt))
                            is NewUserOnboardingResult.LaunchSearch -> _commands.send(Command.FinishAndSubmitSearchQuery(query = result.query))
                            null -> _commands.send(Command.Finish)
                        }
                    }
                    is LinearOnboardingState.Skipped -> _commands.send(Command.OnboardingSkipped)
                }
            }
            .launchIn(viewModelScope)
    }

    // Maps the current step's dialog onto the same viewState/commands the fragment already renders.
    // Reuses setCurrentDialog / setInputScreenPreviewDialog so each dialog's shown pixel fires once.
    private suspend fun applyDialog(dialog: NewUserOnboardingActivityDialog, progress: StepProgress?) {
        when (dialog) {
            is NewUserOnboardingActivityDialog.IntroAnimation -> {
                _commands.send(Command.PlayIntroAnimation(withDuckAi = dialog.withDuckAi))
            }
            NewUserOnboardingActivityDialog.NotificationPermission -> {
                if (!notificationPermissionFlowStarted) {
                    notificationPermissionFlowStarted = true
                    viewModelScope.launch {
                        delay(2.seconds)
                        _commands.send(Command.RequestNotificationPermissions)
                    }
                }
            }
            NewUserOnboardingActivityDialog.SyncRestore -> setCurrentDialog(SYNC_RESTORE)
            NewUserOnboardingActivityDialog.InitialReinstallUser -> {
                _viewState.update { it.copy(isReinstallUser = true) }
                setCurrentDialog(INITIAL_REINSTALL_USER)
            }
            NewUserOnboardingActivityDialog.Initial -> setCurrentDialog(INITIAL)
            NewUserOnboardingActivityDialog.ComparisonChart ->
                setCurrentDialog(COMPARISON_CHART, stepIndicator = progress)
            NewUserOnboardingActivityDialog.AiComparisonChart ->
                setCurrentDialog(AI_COMPARISON_CHART, stepIndicator = progress)
            NewUserOnboardingActivityDialog.DefaultBrowserPrompt -> {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                } else {
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                    orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
                }
            }
            is NewUserOnboardingActivityDialog.AddressBarPosition -> {
                _viewState.update { it.copy(showSplitOption = dialog.showSplitOption) }
                setCurrentDialog(ADDRESS_BAR_POSITION, stepIndicator = progress)
            }
            NewUserOnboardingActivityDialog.InputScreen ->
                setCurrentDialog(INPUT_SCREEN, stepIndicator = progress)
            is NewUserOnboardingActivityDialog.InputScreenPreview ->
                setInputScreenPreviewDialog(isSearchDefault = dialog.isSearchDefault, stepIndicator = progress)
            is NewUserOnboardingActivityDialog.QuickSetup -> {
                _viewState.update {
                    it.copy(
                        showSplitOption = dialog.showSplitOption,
                        hideSetDefaultBrowserRow = dialog.hideSetDefaultBrowserRow,
                        hideAddWidgetRow = dialog.hideAddWidgetRow,
                        isReinstallUser = dialog.isReinstallUser,
                    )
                }
                setCurrentDialog(QUICK_SETUP)
            }
        }
    }

    private fun emit(event: NewUserOnboardingEvent) {
        viewModelScope.launch { orchestrator.onEvent(event) }
    }
}
