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

/**
 * Resolves the orchestrator's current step into one [DialogConfig] and publishes it, so the fragment's render
 * engine has a single value-comparable description of the screen to diff against. Live working state for
 * stateful screens lives in the [ContentValueStore] this view model owns, not in [ViewState].
 */
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
        val stepId: LinearOnboardingStepId? = null,
        val config: DialogConfig? = null,
        val animateEntry: Boolean = true,
    )

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

    /** Blind forward: the engine, through a CTA click or the bound screen's own result, already resolved the event. */
    fun onEvent(event: NewUserOnboardingEvent) = emit(event)

    /** No [ContentInteraction] variants exist yet, so a bound screen has nothing to raise outside the CTA flow. */
    fun onContentInteraction(interaction: ContentInteraction) = Unit

    /**
     * Flips the one-shot entry animation flag once the fragment has rendered [stepId], so a later rotation
     * re-collection of [viewState], which replays its last value rather than recomputing it, snaps instead of
     * replaying the entrance. No-op if the current step has since moved on.
     */
    fun onDialogRendered(stepId: LinearOnboardingStepId) {
        _viewState.update { if (it.stepId == stepId) it.copy(animateEntry = false) else it }
    }

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
        val config = dialogConfigResolver.resolve(dialog, customAiOnboardingStore.isEnabled())
        if (config != null) {
            _viewState.update {
                it.copy(
                    stepId = step.id,
                    config = config.copy(stepIndicator = state.stepIndicatorProgress()),
                    animateEntry = step.id != lastPresentedStepId,
                )
            }
            lastPresentedStepId = step.id
            emit(NewUserOnboardingEvent.Presented)
        } else {
            advancePastUnrenderedDialog(dialog)
        }
    }

    /**
     * Handle commands and dialogs the renderer doesn't support yet by advancing past them, without reporting
     * them as presented.
     */
    private suspend fun advancePastUnrenderedDialog(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            is NewUserOnboardingActivityDialog.IntroAnimation -> emit(NewUserOnboardingEvent.IntroAnimationFinished)

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
