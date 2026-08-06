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
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
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
import com.duckduckgo.app.onboarding.orchestrator.stepIndicatorProgress
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.widget.ui.WidgetCapabilities
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
    private val dispatchers: DispatcherProvider,
    private val widgetCapabilities: WidgetCapabilities,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
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

    init {
        start()
    }

    fun onEvent(event: NewUserOnboardingEvent) = emit(event)

    fun onContentInteraction(interaction: ContentInteraction) = Unit // No-op until dialogs with local state are implemented in follow-ups.

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

    fun checkAddWidgetPromptResult() {
        if (addWidgetPromptFlowStarted) {
            viewModelScope.launch {
                val hasWidget = withContext(dispatchers.io()) { widgetCapabilities.hasInstalledWidgets }
                addWidgetPromptFlowStarted = false
                orchestrator.onEvent(NewUserOnboardingEvent.AddWidgetFinished(widgetAdded = hasWidget))
            }
        }
    }

    private fun recordDefaultBrowserDialogResult(isSet: Boolean) {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = isSet
        val pixelName = if (isSet) AppPixelName.DEFAULT_BROWSER_SET else AppPixelName.DEFAULT_BROWSER_NOT_SET
        pixel.fire(pixelName, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
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
            advancePastUnrenderedDialog(dialog)
        }
    }

    /**
     * Handle commands and dialogs the renderer doesn't support yet by advancing past them, without reporting
     * them as presented.
     *
     * Temporary until all dialogs are implemented in the renderer.
     */
    private suspend fun advancePastUnrenderedDialog(dialog: NewUserOnboardingActivityDialog) {
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

            NewUserOnboardingActivityDialog.SyncRestore -> emit(NewUserOnboardingEvent.SkipRequested)

            NewUserOnboardingActivityDialog.InitialReinstallUser,
            NewUserOnboardingActivityDialog.Initial,
            NewUserOnboardingActivityDialog.AddToDock,
            -> emit(NewUserOnboardingEvent.ContinueClicked)

            NewUserOnboardingActivityDialog.WidgetPrompt -> emit(NewUserOnboardingEvent.WidgetPromptSkipped)

            NewUserOnboardingActivityDialog.InputScreen -> emit(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))

            is NewUserOnboardingActivityDialog.InputScreenPreview -> emit(NewUserOnboardingEvent.ContinueClicked)

            is NewUserOnboardingActivityDialog.QuickSetup -> emit(
                NewUserOnboardingEvent.QuickSetupConfirmed(type = OmnibarType.SINGLE_TOP, withAi = true),
            )

            is NewUserOnboardingActivityDialog.IntroAnimation,
            NewUserOnboardingActivityDialog.ComparisonChart,
            NewUserOnboardingActivityDialog.AiComparisonChart,
            is NewUserOnboardingActivityDialog.AddressBarPosition,
            -> Unit
        }
    }

    private fun emit(event: NewUserOnboardingEvent) {
        viewModelScope.launch { orchestrator.onEvent(event) }
    }
}
