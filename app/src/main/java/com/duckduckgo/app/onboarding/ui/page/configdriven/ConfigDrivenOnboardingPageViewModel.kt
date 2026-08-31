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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanBootstrapper
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingResult
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingStepIds
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportOutcome
import com.duckduckgo.app.onboarding.orchestrator.stepIndicatorProgress
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsResult
import com.duckduckgo.autofill.api.ImportPasswordsFromGoogle.ImportPasswordsStatus
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import com.duckduckgo.onboarding.api.forPlan
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
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
class ConfigDrivenOnboardingPageViewModel @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper,
    private val dialogConfigResolver: DialogConfigResolver,
    private val shownPixels: OnboardingDialogShownPixels,
    private val dispatchers: DispatcherProvider,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val importPasswordsFromGoogle: ImportPasswordsFromGoogle,
) : ViewModel() {

    data class ViewState(
        val screen: Screen? = null,
    )

    sealed interface Screen {

        sealed interface Intro : Screen {
            val withDuckAi: Boolean

            data class Play(override val withDuckAi: Boolean) : Intro

            /**
             * A view already started the intro.
             * Ignore this state if the view played the intro, snap to end state if the view hasn't (for example, when recreated).
             */
            data class Restore(override val withDuckAi: Boolean) : Intro
        }

        data class Dialog(
            val stepId: LinearOnboardingStepId,
            val config: DialogConfig,
            val animateEntry: Boolean,
        ) : Screen

        /**
         * The flow is on a step this view draws nothing for and there is no earlier screen to keep showing, which
         * only happens on a fresh view model, for example after a mid-flow re-entry.
         */
        data object None : Screen
    }

    sealed interface Command {
        data object RequestNotificationPermissions : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object LaunchAddWidgetPrompt : Command
        data object Finish : Command
        data class FinishAndSubmitSearchQuery(val query: String) : Command
        data class FinishAndSubmitChatPrompt(val prompt: String) : Command
        data object OnboardingSkipped : Command
        data object HandOffToBrowserActivity : Command
        data class ShowQuickSetupDefaultBrowserDialog(val intent: Intent) : Command
        data object OpenDefaultBrowserSystemSettings : Command
        data object ShowRemoveWidgetBottomSheet : Command
        data class ShowQuickSetupAddressBarPositionBottomSheet(
            val initialSelection: OmnibarType,
            val showSplitOption: Boolean,
        ) : Command
        data class ShowQuickSetupSearchOptionsBottomSheet(val initialWithAi: Boolean) : Command
        data object LaunchPasswordImport : Command
        data object ShowPasswordImportError : Command
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    /** Live state for stateful screens; survives rotation with this view model, exposed for the fragment's binders. */
    val contentValues = ContentValueStore()

    /** Last step id a [DialogConfig] was published for; drives the [ViewState.animateEntry] policy. */
    private var lastPresentedStepId: LinearOnboardingStepId? = null

    private var notificationPermissionFlowStarted = false

    private var addWidgetPromptFlowStarted = false

    private var quickSetupDefaultBrowserDialogShown = false

    init {
        start()
    }

    fun onEvent(event: NewUserOnboardingEvent) = emit(event)

    fun onContentInteraction(interaction: ContentInteraction) {
        when (interaction) {
            is ContentInteraction.SubmitInputPreview -> emit(
                NewUserOnboardingEvent.InputDemoQuerySubmitted(
                    query = interaction.query,
                    isChat = interaction.isChat,
                    fromSuggestion = interaction.fromSuggestion,
                ),
            )

            ContentInteraction.QuickSetupEditAddressBarPosition -> {
                val screen = currentQuickSetup() ?: return
                viewModelScope.launch {
                    _commands.send(
                        Command.ShowQuickSetupAddressBarPositionBottomSheet(
                            initialSelection = screen.state.value.addressBarPosition,
                            showSplitOption = screen.content.showSplitOption,
                        ),
                    )
                }
            }

            ContentInteraction.QuickSetupEditSearchOptions -> {
                val screen = currentQuickSetup() ?: return
                viewModelScope.launch {
                    _commands.send(Command.ShowQuickSetupSearchOptionsBottomSheet(initialWithAi = screen.state.value.withAi))
                }
            }

            // The switch has already flipped itself, so the store has to record that before any side effect: a
            // later corrective write of the old value (declined system dialog, resume resync) would otherwise be
            // deduped as a no-change and never reach the binder.
            is ContentInteraction.QuickSetupSetDefaultBrowser -> {
                currentQuickSetup()?.state?.update { it.copy(defaultBrowserChecked = interaction.checked) }
                if (interaction.checked) requestDefaultBrowser() else openDefaultBrowserSettings()
            }

            is ContentInteraction.QuickSetupAddWidget -> {
                currentQuickSetup()?.state?.update { it.copy(widgetChecked = interaction.checked) }
                viewModelScope.launch {
                    _commands.send(if (interaction.checked) Command.LaunchAddWidgetPrompt else Command.ShowRemoveWidgetBottomSheet)
                }
            }

            is ContentInteraction.SelectSingleChoiceOption -> emit(NewUserOnboardingEvent.SingleChoiceConfirmed(interaction.option))
        }
    }

    fun onDialogRendered(stepId: LinearOnboardingStepId) {
        _viewState.update { state ->
            val screen = state.screen
            // The dialog for this step has been rendered.
            // Disable entry animation for potential re-draws (like config change/rotation).
            if (screen is Screen.Dialog && screen.stepId == stepId) {
                state.copy(screen = screen.copy(animateEntry = false))
            } else {
                state
            }
        }
    }

    fun onIntroAnimationStarted() {
        _viewState.update { state ->
            val screen = state.screen
            if (screen is Screen.Intro.Play) {
                state.copy(screen = Screen.Intro.Restore(withDuckAi = screen.withDuckAi))
            } else {
                state
            }
        }
    }

    fun onIntroAnimationFinished() = emit(NewUserOnboardingEvent.IntroAnimationFinished)

    fun onResume() {
        syncQuickSetupSwitches()
        checkAddWidgetPromptResult()
    }

    fun notificationPermissionFlowFinished(granted: Boolean?) {
        if (granted == true) {
            pixel.fire(AppPixelName.NOTIFICATIONS_ENABLED, mapOf(PixelParameter.FROM_ONBOARDING to true.toString()))
        }
        emit(NewUserOnboardingEvent.NotificationPermissionFinished(granted = granted))
    }

    /**
     * Fires as the runtime permission dialog is about to be requested. That dialog is the screen for this step,
     * so this doubles as its shown signal.
     */
    fun notificationRuntimePermissionRequested() {
        pixel.fire(AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
        emit(NewUserOnboardingEvent.Presented)
    }

    fun onDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true)
        emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
    }

    fun onDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false)
        emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
    }

    fun onAddressBarBottomSheetResult(type: OmnibarType) {
        currentQuickSetup()?.state?.update { it.copy(addressBarPosition = type) }
    }

    fun onSearchOptionsBottomSheetResult(withAi: Boolean) {
        currentQuickSetup()?.state?.update { it.copy(withAi = withAi) }
    }

    /** Quick setup's own default-browser prompt: it never advances the step, which only moves on confirmation. */
    fun onQuickSetupDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true, fireTelemetry = false)
    }

    fun onQuickSetupDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false, fireTelemetry = false)
        currentQuickSetup()?.state?.update { it.copy(defaultBrowserChecked = false) }
    }

    /**
     * Re-reads the OS state behind quick setup's two switches. Also called by the fragment when the system
     * settings intent cannot be launched, since no activity starts and no later [onResume] follows.
     */
    fun syncQuickSetupSwitches() {
        val screen = currentQuickSetup() ?: return
        viewModelScope.launch {
            val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
            }
            screen.state.update { it.copy(defaultBrowserChecked = isDefault, widgetChecked = hasWidget) }
        }
    }

    fun checkAddWidgetPromptResult() {
        if (addWidgetPromptFlowStarted) {
            viewModelScope.launch {
                val hasWidget = withContext(dispatchers.io()) { widgetCapabilities.hasInstalledWidgets }
                addWidgetPromptFlowStarted = false
                orchestrator.onEvent(NewUserOnboardingEvent.AddWidgetFinished(widgetAdded = hasWidget))
            }
        }
    }

    fun onPasswordImportRetry() = launchPasswordImport()

    fun onPasswordImportResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            cancelPasswordImport()
            return
        }
        when (importPasswordsFromGoogle.parseResult(data)) {
            is ImportPasswordsResult.Success -> showImportOutcome()
            is ImportPasswordsResult.UserCancelled -> cancelPasswordImport()
            is ImportPasswordsResult.Error.Transient -> showImportErrorDialog()
            is ImportPasswordsResult.Error.Permanent -> failPasswordImport()
        }
    }

    private fun launchPasswordImport() {
        viewModelScope.launch { _commands.send(Command.LaunchPasswordImport) }
    }

    private fun showImportOutcome() {
        val state = importCompleteState()
        state.value = ImportCompleteContentState.Parsing
        emit(NewUserOnboardingEvent.PasswordImportWebFlowFinished(PasswordImportOutcome.SUCCESS))
        viewModelScope.launch {
            val finished = importPasswordsFromGoogle.importStatus().filterIsInstance<ImportPasswordsStatus.Finished>().firstOrNull()
            if (finished == null) {
                state.value = ImportCompleteContentState.Failed
                emit(NewUserOnboardingEvent.PasswordImportParsed(PasswordImportOutcome.PERMANENT_ERROR))
                return@launch
            }
            state.value = ImportCompleteContentState.Finished(imported = finished.imported, skipped = finished.skipped)
            emit(NewUserOnboardingEvent.PasswordImportParsed(PasswordImportOutcome.SUCCESS))
        }
    }

    private fun importCompleteState(): MutableStateFlow<ImportCompleteContentState> =
        contentValues.contentState(NewUserOnboardingStepIds.PASSWORD_IMPORT_COMPLETE) { ImportCompleteContentState.Parsing }

    private fun failPasswordImport() {
        importCompleteState().value = ImportCompleteContentState.Failed
        emit(NewUserOnboardingEvent.PasswordImportWebFlowFinished(PasswordImportOutcome.PERMANENT_ERROR))
    }

    private fun showImportErrorDialog() {
        emit(NewUserOnboardingEvent.PasswordImportWebFlowFinished(PasswordImportOutcome.TRANSIENT_ERROR))
        viewModelScope.launch { _commands.send(Command.ShowPasswordImportError) }
    }

    private fun cancelPasswordImport() {
        emit(NewUserOnboardingEvent.PasswordImportWebFlowFinished(PasswordImportOutcome.CANCELLED))
    }

    private fun requestDefaultBrowser() {
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

    private fun openDefaultBrowserSettings() {
        viewModelScope.launch { _commands.send(Command.OpenDefaultBrowserSystemSettings) }
    }

    private fun currentQuickSetup(): QuickSetupScreen? {
        val dialog = _viewState.value.screen as? Screen.Dialog ?: return null
        val content = dialog.config.content as? ContentConfig.QuickSetup ?: return null
        return QuickSetupScreen(content, contentValues.contentState(dialog.stepId, content))
    }

    private class QuickSetupScreen(
        val content: ContentConfig.QuickSetup,
        val state: MutableStateFlow<QuickSetupContentState>,
    )

    private fun recordDefaultBrowserDialogResult(
        isSet: Boolean,
        fireTelemetry: Boolean = true,
    ) {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = isSet
        if (fireTelemetry) {
            val pixelName = if (isSet) AppPixelName.DEFAULT_BROWSER_SET else AppPixelName.DEFAULT_BROWSER_NOT_SET
            pixel.fire(pixelName, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
        }
    }

    private fun start() {
        viewModelScope.launch {
            if (orchestrator.state.value is LinearOnboardingState.NotStarted) {
                // Safeguard in case OnboardingActivity is restored after process death and does not route
                // through LaunchViewModel; restart the plan so onboarding resumes from the top.
                newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()
            }
            observeOrchestratorState()
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
                                // This view model only drives the new-user onboarding plan; any other host is
                                // not its screen.
                                return@onEach
                            }
                        }
                        if (step is NewUserOnboardingActivityStep) {
                            applyStep(step, state)
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

    private suspend fun applyStep(
        step: NewUserOnboardingActivityStep,
        state: LinearOnboardingState.InProgress,
    ) {
        val dialog = step.resolveDialog()

        if (dialog is NewUserOnboardingActivityDialog.IntroAnimation) {
            _viewState.update {
                it.copy(
                    screen = if (it.screen is Screen.Intro.Restore) {
                        Screen.Intro.Restore(withDuckAi = dialog.withDuckAi)
                    } else {
                        Screen.Intro.Play(withDuckAi = dialog.withDuckAi)
                    },
                )
            }
            return
        }

        val config = dialogConfigResolver.resolve(dialog, customAiOnboardingStore.isEnabled())
        if (config != null) {
            shownPixels.fireFor(dialog)
            _viewState.update {
                it.copy(
                    screen = Screen.Dialog(
                        stepId = step.id,
                        config = config.copy(stepIndicator = state.stepIndicatorProgress()),
                        animateEntry = step.id != lastPresentedStepId,
                    ),
                )
            }
            lastPresentedStepId = step.id
            emit(NewUserOnboardingEvent.Presented)
        } else {
            // If there's no new dialog to draw (e.g. we're displaying a system prompt), keep the current one.
            // None only when there was never anything to keep.
            _viewState.update { state -> if (state.screen == null) state.copy(screen = Screen.None) else state }
            handleCommandOnlyDialog(dialog)
        }
    }

    /**
     * Handler for dialogs that have no card, only a side effect.
     */
    private suspend fun handleCommandOnlyDialog(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            NewUserOnboardingActivityDialog.NotificationPermission -> {
                if (!notificationPermissionFlowStarted) {
                    notificationPermissionFlowStarted = true
                    viewModelScope.launch {
                        delay(2.seconds)
                        _commands.send(Command.RequestNotificationPermissions)
                    }
                }
            }

            NewUserOnboardingActivityDialog.DefaultBrowserPrompt -> {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                } else {
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                    emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
                }
            }

            NewUserOnboardingActivityDialog.AddWidget -> {
                addWidgetPromptFlowStarted = true
                _commands.send(Command.LaunchAddWidgetPrompt)
            }

            NewUserOnboardingActivityDialog.ImportPasswordsLaunch -> launchPasswordImport()

            NewUserOnboardingActivityDialog.SyncRestore,
            NewUserOnboardingActivityDialog.InitialReinstallUser,
            NewUserOnboardingActivityDialog.Initial,
            is NewUserOnboardingActivityDialog.IntroAnimation,
            NewUserOnboardingActivityDialog.ComparisonChart,
            NewUserOnboardingActivityDialog.AiComparisonChart,
            is NewUserOnboardingActivityDialog.SegmentedComparisonChart,
            NewUserOnboardingActivityDialog.DownloadReason,
            NewUserOnboardingActivityDialog.AddToDock,
            NewUserOnboardingActivityDialog.WidgetPrompt,
            NewUserOnboardingActivityDialog.ImportPasswords,
            NewUserOnboardingActivityDialog.ImportComplete,
            is NewUserOnboardingActivityDialog.AddressBarPosition,
            NewUserOnboardingActivityDialog.InputScreen,
            is NewUserOnboardingActivityDialog.InputScreenPreview,
            is NewUserOnboardingActivityDialog.QuickSetup,
            is NewUserOnboardingActivityDialog.PreferenceSelector,
            is NewUserOnboardingActivityDialog.SingleChoice,
            is NewUserOnboardingActivityDialog.TogglePosition,
            is NewUserOnboardingActivityDialog.DuckAiState,
            -> Unit
        }
    }

    private fun emit(event: NewUserOnboardingEvent) {
        viewModelScope.launch { orchestrator.onEvent(event) }
    }
}
