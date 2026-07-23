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
 * Slim, config-driven counterpart to [com.duckduckgo.app.onboarding.ui.page.BrandDesignUpdatePageViewModel].
 * Instead of a per-dialog [ViewState] field soup, it publishes one [DialogConfig] per step (resolved by
 * [DialogConfigResolver]) plus a VM-owned [ContentValueStore] for stateful screens' live working state.
 *
 * Ported from the legacy VM: orchestrator observation, command routing, quick-setup handlers, and the
 * notification/default-browser/widget flows. Not ported: per-dialog animation bookkeeping
 * (`hasAnimatedCurrentDialog` / `onDialogAnimationStarted` / `SkipDialogAnimation`) — tap-to-skip is now the
 * fragment calling the rendering engine's `skipRunningAnimations()` directly, since the engine (not this VM)
 * owns the in-flight animators.
 */
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
) : ViewModel() {

    data class ViewState(
        val stepId: LinearOnboardingStepId? = null,
        val config: DialogConfig? = null,
        val animateEntry: Boolean = true,
        val hasPlayedIntroAnimation: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    /** VM-owned live state for stateful screens; survives rotation with the VM, exposed for the fragment's binders. */
    val contentValues = ContentValueStore()

    /** Last step id a [DialogConfig] was published for; drives the [ViewState.animateEntry] policy. */
    private var lastPresentedStepId: LinearOnboardingStepId? = null

    private var quickSetupDefaultBrowserDialogShown = false

    private var notificationPermissionFlowStarted = false

    private var addWidgetPromptFlowStarted = false

    init {
        start()
    }

    sealed interface Command {
        data class PlayIntroAnimation(val withDuckAi: Boolean = false) : Command
        data object RequestNotificationPermissions : Command
        data class ShowDefaultBrowserDialog(val intent: Intent) : Command
        data object OpenDefaultBrowserSystemSettings : Command
        data class ShowQuickSetupDefaultBrowserDialog(val intent: Intent) : Command
        data object LaunchAddWidgetPrompt : Command
        data object ShowRemoveWidgetBottomSheet : Command
        data class ShowQuickSetupAddressBarPositionBottomSheet(
            val initialSelection: OmnibarType,
            val showSplitOption: Boolean,
        ) : Command
        data class ShowQuickSetupSearchOptionsBottomSheet(val initialWithAi: Boolean) : Command
        data object Finish : Command
        data class FinishAndSubmitSearchQuery(val query: String) : Command
        data class FinishAndSubmitChatPrompt(val prompt: String) : Command
        data object OnboardingSkipped : Command
        data object HandOffToBrowserActivity : Command
    }

    /** Blind forward: the engine (CTA click or bound content's own result) already resolved the event. */
    fun onEvent(event: NewUserOnboardingEvent) = emit(event)

    fun onContentInteraction(interaction: ContentInteraction) {
        when (interaction) {
            ContentInteraction.EditAddressBarPosition -> {
                val quickSetup = currentQuickSetup() ?: return
                val currentPosition = contentValues.contentState(quickSetup).value.addressBarPosition
                viewModelScope.launch {
                    _commands.send(
                        Command.ShowQuickSetupAddressBarPositionBottomSheet(
                            initialSelection = currentPosition,
                            showSplitOption = quickSetup.showSplitOption,
                        ),
                    )
                }
            }
            ContentInteraction.EditSearchOptions -> {
                val quickSetup = currentQuickSetup() ?: return
                val currentWithAi = contentValues.contentState(quickSetup).value.withAi
                viewModelScope.launch {
                    _commands.send(Command.ShowQuickSetupSearchOptionsBottomSheet(initialWithAi = currentWithAi))
                }
            }
            is ContentInteraction.SetDefaultBrowserToggled ->
                if (interaction.checked) onQuickSetupSetAsDefaultClicked() else onQuickSetupSetAsDefaultUnchecked()
            is ContentInteraction.AddWidgetToggled ->
                if (interaction.checked) onQuickSetupAddHomescreenWidgetClicked() else onQuickSetupRemoveHomescreenWidgetClicked()
        }
    }

    /**
     * Flips the one-shot entry animation flag once the fragment has rendered [stepId], so a later rotation
     * re-collection of [viewState] (which replays its last value, not a fresh computation) snaps instead of
     * replaying the entrance animation. No-op if the current step has since moved on.
     */
    fun onDialogRendered(stepId: LinearOnboardingStepId) {
        _viewState.update { if (it.stepId == stepId) it.copy(animateEntry = false) else it }
    }

    fun onResume() {
        val quickSetup = currentQuickSetup() ?: return
        viewModelScope.launch(dispatchers.io()) {
            val isDefault = defaultBrowserDetector.isDefaultBrowser()
            val hasWidget = widgetCapabilities.hasInstalledWidgets
            contentValues.contentState(quickSetup).update { it.copy(defaultBrowserChecked = isDefault, widgetChecked = hasWidget) }
        }
        checkAddWidgetPromptResult()
    }

    fun onIntroAnimationFinished() {
        _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
        emit(NewUserOnboardingEvent.IntroAnimationFinished)
    }

    /**
     * Merges legacy's `notificationRuntimePermissionGranted()`/`notificationRuntimePermissionDenied()` (which
     * only stashed the result in a field) and `notificationPermissionFlowFinished()` (which read that field)
     * into a single call taking the result directly, since the fragment already has it at the callback site.
     */
    fun notificationPermissionFlowFinished(granted: Boolean?) {
        if (granted == true) {
            pixel.fire(AppPixelName.NOTIFICATIONS_ENABLED, mapOf(PixelParameter.FROM_ONBOARDING to true.toString()))
        }
        emit(NewUserOnboardingEvent.NotificationPermissionFinished(granted = granted))
    }

    /**
     * Fires as the runtime permission dialog is about to be requested; this dialog is command-only (no [DialogConfig]), so
     * this doubles as its "shown" telemetry, ported from legacy's identically-named method.
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

    /**
     * Result of the quick-setup "set as default" toggle's system dialog. Unlike [onDefaultBrowserSet], this
     * doesn't advance the orchestrator (the quick-setup step only advances on [NewUserOnboardingEvent.QuickSetupConfirmed])
     * and fires no telemetry, ported from legacy's identically-named methods.
     */
    fun onQuickSetupDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true, fireTelemetry = false)
    }

    fun onQuickSetupDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false, fireTelemetry = false)
    }

    fun onAddressBarBottomSheetResult(type: OmnibarType) {
        currentQuickSetup()?.let { quickSetup ->
            contentValues.contentState(quickSetup).update { it.copy(addressBarPosition = type) }
        }
    }

    fun onSearchOptionsBottomSheetResult(withAi: Boolean) {
        currentQuickSetup()?.let { quickSetup ->
            contentValues.contentState(quickSetup).update { it.copy(withAi = withAi) }
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

    private fun onQuickSetupSetAsDefaultClicked() {
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

    private fun onQuickSetupSetAsDefaultUnchecked() {
        viewModelScope.launch {
            _commands.send(Command.OpenDefaultBrowserSystemSettings)
        }
    }

    private fun onQuickSetupAddHomescreenWidgetClicked() {
        viewModelScope.launch {
            _commands.send(Command.LaunchAddWidgetPrompt)
        }
    }

    private fun onQuickSetupRemoveHomescreenWidgetClicked() {
        viewModelScope.launch {
            _commands.send(Command.ShowRemoveWidgetBottomSheet)
        }
    }

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

    private fun currentQuickSetup(): ContentConfig.QuickSetup? = _viewState.value.config?.content as? ContentConfig.QuickSetup

    private fun start() {
        seedHasPlayedIntroAnimation()
        viewModelScope.launch {
            if (orchestrator.state.value is LinearOnboardingState.NotStarted) {
                // Safeguard in case OnboardingActivity is restored after process death and does
                // not route through LaunchViewModel; restart the plan so onboarding resumes from the top.
                newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()
            }
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

    private suspend fun isCustomAiFlow(): Boolean = customAiOnboardingStore.isEnabled()

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

    // Maps the current step's dialog onto either a rendered DialogConfig (fires the shown pixel and the
    // one-shot animateEntry policy) or a command-only side effect the fragment has no config to render for.
    private suspend fun applyStep(step: NewUserOnboardingActivityStep, state: LinearOnboardingState.InProgress) {
        val dialog = step.resolveDialog()
        val config = dialogConfigResolver.resolve(dialog, isCustomAiFlow())
        if (config != null) {
            shownPixels.fireFor(dialog)
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
            handleCommandOnlyDialog(dialog)
        }
    }

    // Ported from legacy applyDialog's command-only branches (IntroAnimation / NotificationPermission /
    // DefaultBrowserPrompt / AddWidget) — the only dialogs DialogConfigResolver.resolve() maps to null.
    private suspend fun handleCommandOnlyDialog(dialog: NewUserOnboardingActivityDialog) {
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
            NewUserOnboardingActivityDialog.DefaultBrowserPrompt -> {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                } else {
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                    orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
                }
            }
            NewUserOnboardingActivityDialog.AddWidget -> {
                addWidgetPromptFlowStarted = true
                _commands.send(Command.LaunchAddWidgetPrompt)
            }
            else -> Unit // unreachable: DialogConfigResolver.resolve() returns null only for the four dialogs above
        }
    }

    private fun emit(event: NewUserOnboardingEvent) {
        viewModelScope.launch { orchestrator.onEvent(event) }
    }
}
