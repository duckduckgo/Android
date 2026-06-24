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
import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
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
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager.QuickSetupExperimentVariant
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.forPlan
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(FragmentScope::class)
class BrandDesignUpdatePageViewModel @Inject constructor(
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val context: Context,
    private val pixel: Pixel,
    private val appInstallStore: AppInstallStore,
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val onboardingStore: OnboardingStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val duckChat: DuckChat,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
    private val duckAiOnboardingAvailability: DuckAiOnboardingAvailability,
    private val onboardingQuickSetupExperimentManager: OnboardingQuickSetupExperimentManager,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val syncAutoRestore: SyncAutoRestore,
    private val brandDesignOnboardingPixelSender: BrandDesignOnboardingPixelSender,
    private val orchestrator: LinearOnboardingOrchestrator,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
) : ViewModel() {

    private val isReinstallDeferred: Deferred<Boolean> by lazy {
        viewModelScope.async(dispatchers.io()) { appBuildConfig.isAppReinstall() }
    }

    // Lazy so it never starts in orchestrator mode (there the sync_restore precondition owns canRestore).
    private val canRestoreDeferred: Deferred<Boolean> by lazy {
        viewModelScope.async(dispatchers.io()) {
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
        }
    }

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
        val currentPageNumber: Int? = null,
        // Total steps in the indicator. Set alongside currentPageNumber from the plan-derived StepProgress
        // (orchestrator flow) or the legacy flow's fixed 3-step sequence. Only read while an indicator is shown.
        val maxPageCount: Int = DEFAULT_STEP_COUNT,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var quickSetupDefaultBrowserDialogShown: Boolean = false

    private var notificationPermissionFlowStarted = false

    // Which flow drives this run, chosen once at construction. Legacy = the in-VM state machine;
    // Orchestrator = translate fragment callbacks to LinearOnboardingOrchestrator events and render its
    // state. Either way the intra-dialog interaction methods below are shared and behave identically.
    // After the orchestrator rollout, delete LegacyFlow (and the now-unused legacy-only helpers) and make
    // this always OrchestratorFlow.
    private val flow: OnboardingFlow =
        if (orchestrator.state.value is LinearOnboardingState.NotStarted) LegacyFlow() else OrchestratorFlow()

    init {
        flow.start()
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
        currentPageNumber: Int? = null,
        totalSteps: Int = DEFAULT_STEP_COUNT,
    ) {
        _viewState.update {
            it.copy(
                currentDialog = dialogType,
                hasAnimatedCurrentDialog = false,
                currentPageNumber = currentPageNumber,
                maxPageCount = totalSteps,
            )
        }
        fireDialogShownPixel(dialogType)
    }

    private fun setInputScreenPreviewDialog(
        isSearchDefault: Boolean,
        currentPageNumber: Int?,
        totalSteps: Int = DEFAULT_STEP_COUNT,
    ) {
        _viewState.update {
            it.copy(
                currentDialog = INPUT_SCREEN_PREVIEW,
                hasAnimatedCurrentDialog = false,
                inputScreenPreviewSearchSuggestions = onboardingStore.getSearchOptions(),
                inputScreenPreviewChatSuggestions = onboardingStore.getChatSuggestions(),
                inputScreenPreviewIsSearchSelected = isSearchDefault,
                currentPageNumber = currentPageNumber,
                maxPageCount = totalSteps,
            )
        }
        fireDialogShownPixel(INPUT_SCREEN_PREVIEW)
    }

    private fun fireDialogShownPixel(dialogType: PreOnboardingDialogType) {
        val context = pixelContext()
        when (dialogType) {
            SYNC_RESTORE -> pixel.fire(PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
            INITIAL_REINSTALL_USER -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            INITIAL -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            COMPARISON_CHART -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            AI_COMPARISON_CHART -> pixel.fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
            SKIP_ONBOARDING_OPTION -> pixel.fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
            ADDRESS_BAR_POSITION -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            INPUT_SCREEN -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
            INPUT_SCREEN_PREVIEW, QUICK_SETUP -> Unit
        }
        dialogType.toEvent(OnboardingAction.Shown)?.let { brandDesignOnboardingPixelSender.fire(context, it) }
    }

    fun onIntroAnimationStarted() {
        _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
    }

    fun onIntroAnimationFinished() = flow.onIntroAnimationFinished()

    fun loadDaxDialog() = flow.loadDaxDialog()

    fun onPrimaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        fireBrandDesignPrimaryClickedPixel(currentDialog)
        flow.onPrimaryCta(currentDialog)
    }

    fun onSecondaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        fireBrandDesignSecondaryClickedPixel(currentDialog)
        flow.onSecondaryCta(currentDialog)
    }

    private fun fireBrandDesignPrimaryClickedPixel(dialog: PreOnboardingDialogType) {
        val action = OnboardingAction.PrimaryClick(
            addressBarPosition = _viewState.value.selectedAddressBarPosition,
            withAi = _viewState.value.inputScreenSelected,
        )
        dialog.toEvent(action)?.let { brandDesignOnboardingPixelSender.fire(pixelContext(), it) }
    }

    private fun fireBrandDesignSecondaryClickedPixel(dialog: PreOnboardingDialogType) {
        dialog.toEvent(OnboardingAction.SecondaryClick)?.let { brandDesignOnboardingPixelSender.fire(pixelContext(), it) }
    }

    fun onInputModeDemoQuerySubmitted(
        query: String,
        isChat: Boolean,
        fromSuggestion: Boolean,
    ) {
        if (isChat) {
            onboardingStore.setChatOnboardingVariant()
        } else {
            onboardingStore.setSearchOnboardingVariant()
        }
        brandDesignOnboardingPixelSender.fire(
            pixelContext(),
            OnboardingPixelEvent.TryASearchClicked(fromSuggestion = fromSuggestion, isChat = isChat),
        )
        flow.onInputModeDemoQuerySubmitted(query = query, isChat = isChat)
    }

    fun onDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true)
        fireBrandDesignSetDefaultResult(isDdgDefault = true)
        flow.onDefaultBrowserResult(isDefaultBrowser = true)
    }

    fun onDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false)
        fireBrandDesignSetDefaultResult(isDdgDefault = false)
        flow.onDefaultBrowserResult(isDefaultBrowser = false)
    }

    private fun fireBrandDesignSetDefaultResult(isDdgDefault: Boolean) {
        brandDesignOnboardingPixelSender.fire(pixelContext(), OnboardingPixelEvent.SetDefaultConfirmed(isDdgDefault = isDdgDefault))
    }

    private fun pixelContext(isReinstallUser: Boolean = _viewState.value.isReinstallUser): OnboardingPixelContext =
        OnboardingPixelContext(isReinstallUser = isReinstallUser)

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
        // The notification prompt is shown before loadDaxDialog resolves reinstall status into
        // viewState, so resolve it directly via the shared deferred to report the correct install type.
        viewModelScope.launch {
            brandDesignOnboardingPixelSender.fire(
                pixelContext(isReinstallUser = isReinstallDeferred.await()),
                OnboardingPixelEvent.NotificationsShown,
            )
        }
    }

    fun notificationRuntimePermissionGranted() {
        pixel.fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
        viewModelScope.launch {
            brandDesignOnboardingPixelSender.fire(
                pixelContext(isReinstallUser = isReinstallDeferred.await()),
                OnboardingPixelEvent.NotificationsConfirmed(granted = true),
            )
        }
    }

    fun notificationRuntimePermissionDenied() {
        viewModelScope.launch {
            brandDesignOnboardingPixelSender.fire(
                pixelContext(isReinstallUser = isReinstallDeferred.await()),
                OnboardingPixelEvent.NotificationsConfirmed(granted = false),
            )
        }
    }

    private suspend fun showQuickSetupDialog() {
        val splitEnabled = isSplitOmnibarEnabled()
        val (isDefault, hasWidget) = withContext(dispatchers.io()) {
            defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
        }
        _viewState.update {
            it.copy(
                showSplitOption = splitEnabled,
                hideSetDefaultBrowserRow = isDefault,
                hideAddWidgetRow = hasWidget,
            )
        }
        setCurrentDialog(QUICK_SETUP)
    }

    private suspend fun isSplitOmnibarEnabled(): Boolean =
        withContext(dispatchers.io()) {
            androidBrowserConfigFeature.splitOmnibar().isEnabled() &&
                androidBrowserConfigFeature.splitOmnibarWelcomePage().isEnabled()
        }

    private suspend fun applyAddressBarPositionSelection(fireTelemetry: Boolean = true) {
        val selected = _viewState.value.selectedAddressBarPosition
        val resolved = when {
            selected == OmnibarType.SPLIT && !isSplitOmnibarEnabled() -> OmnibarType.SINGLE_TOP
            else -> selected
        }
        settingsDataStore.omnibarType = resolved
        if (fireTelemetry) {
            fireAddressBarPositionTelemetry(resolved)
        }
    }

    private fun fireAddressBarPositionTelemetry(resolved: OmnibarType) {
        when (resolved) {
            OmnibarType.SINGLE_BOTTOM -> pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SPLIT -> pixel.fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SINGLE_TOP -> Unit
        }
    }

    private suspend fun applyInputScreenSelection(fireTelemetry: Boolean = true) {
        val inputSelected = _viewState.value.inputScreenSelected
        if (fireTelemetry) {
            fireInputScreenSelectionTelemetry(inputSelected)
        }
        duckChat.setCosmeticInputScreenUserSetting(inputSelected)
        onboardingStore.storeInputScreenSelection(inputSelected)
    }

    private fun fireInputScreenSelectionTelemetry(inputSelected: Boolean) {
        if (inputSelected) {
            pixel.fire(PREONBOARDING_AICHAT_SELECTED)
            inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = _viewState.value.isReinstallUser)
        } else {
            pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
        }
    }

    private fun skipDialogAnimations() {
        viewModelScope.launch {
            _commands.send(Command.SkipDialogAnimation)
        }
    }

    // Drives how the page advances between dialogs. The intra-dialog interaction methods above
    // (quick-setup rows, widgets, animations, notification callbacks) are shared and behave the same
    // regardless of which flow is active.
    private interface OnboardingFlow {
        fun start()
        fun onIntroAnimationFinished()
        fun loadDaxDialog()
        fun onPrimaryCta(dialog: PreOnboardingDialogType)
        fun onSecondaryCta(dialog: PreOnboardingDialogType)
        fun onInputModeDemoQuerySubmitted(query: String, isChat: Boolean)
        fun onDefaultBrowserResult(isDefaultBrowser: Boolean)
    }

    // The legacy in-VM state machine. Behaviour unchanged from before the orchestrator migration.
    private inner class LegacyFlow : OnboardingFlow {

        override fun start() {
            viewModelScope.launch(dispatchers.io()) {
                _commands.send(Command.PlayIntroAnimation())
            }
        }

        override fun onIntroAnimationFinished() {
            if (notificationPermissionFlowStarted) return
            notificationPermissionFlowStarted = true
            viewModelScope.launch {
                delay(2.seconds)
                _commands.send(Command.RequestNotificationPermissions)
            }
        }

        override fun loadDaxDialog() {
            viewModelScope.launch {
                val canRestore = withTimeoutOrNull(BLOCK_STORE_TIMEOUT_MS) {
                    canRestoreDeferred.await()
                } ?: false

                val isReinstall = isReinstallDeferred.await()

                val dialogType = when {
                    canRestore -> {
                        logcat(LogPriority.INFO) { "Sync-AutoRestore: first dialog=SYNC_RESTORE" }
                        SYNC_RESTORE
                    }
                    isReinstall -> INITIAL_REINSTALL_USER
                    else -> INITIAL
                }
                _viewState.update {
                    it.copy(
                        isReinstallUser = isReinstall,
                        currentDialog = dialogType,
                        hasAnimatedCurrentDialog = false,
                    )
                }
                fireDialogShownPixel(dialogType)
            }
        }

        override fun onPrimaryCta(dialog: PreOnboardingDialogType) {
            when (dialog) {
                SYNC_RESTORE -> {
                    viewModelScope.launch {
                        logcat { "Sync-AutoRestore: user accepted restore, calling restoreSyncAccount()" }
                        pixel.fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
                        syncAutoRestore.restoreSyncAccount()
                        setCurrentDialog(COMPARISON_CHART, currentPageNumber = 1)
                    }
                }

                INITIAL, INITIAL_REINSTALL_USER -> setCurrentDialog(COMPARISON_CHART, currentPageNumber = 1)

                COMPARISON_CHART -> {
                    viewModelScope.launch {
                        val isDDGDefaultBrowser =
                            if (defaultRoleBrowserDialog.shouldShowDialog()) {
                                val intent = defaultRoleBrowserDialog.createIntent(context)
                                if (intent != null) {
                                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                                } else {
                                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                                    _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
                                    setCurrentDialog(ADDRESS_BAR_POSITION, currentPageNumber = 2)
                                }
                                false
                            } else {
                                _commands.send(Command.Finish)
                                true
                            }
                        pixel.fire(
                            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
                            mapOf(PixelParameter.DEFAULT_BROWSER to isDDGDefaultBrowser.toString()),
                        )
                    }
                }

                AI_COMPARISON_CHART -> {
                    // Unreachable in both paths today (no AI_COMPARISON_CHART step / trigger).
                }

                SKIP_ONBOARDING_OPTION -> {
                    viewModelScope.launch {
                        _commands.send(Command.OnboardingSkipped)
                        pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                        duckChat.setInputScreenUserSetting(true)
                    }
                }

                ADDRESS_BAR_POSITION -> {
                    viewModelScope.launch {
                        applyAddressBarPositionSelection()
                        setCurrentDialog(INPUT_SCREEN, currentPageNumber = 3)
                    }
                }

                INPUT_SCREEN -> {
                    viewModelScope.launch {
                        applyInputScreenSelection()
                        if (_viewState.value.inputScreenSelected && duckAiOnboardingAvailability.isDuckAiOnboardingEnabled()) {
                            setInputScreenPreviewDialog(isSearchDefault = true, currentPageNumber = null)
                        } else {
                            _commands.send(Command.Finish)
                        }
                    }
                }

                INPUT_SCREEN_PREVIEW -> {
                    viewModelScope.launch {
                        _commands.send(Command.Finish)
                    }
                }

                QUICK_SETUP -> {
                    viewModelScope.launch {
                        applyAddressBarPositionSelection(fireTelemetry = false)
                        applyInputScreenSelection(fireTelemetry = false)
                        val state = _viewState.value
                        brandDesignOnboardingPixelSender.fire(
                            context = pixelContext(),
                            event = OnboardingPixelEvent.QuickSetupClicked(
                                addressBarPosition = state.selectedAddressBarPosition,
                                inputScreenSelected = state.inputScreenSelected,
                            ),
                        )
                        _commands.send(Command.OnboardingSkipped)
                    }
                }
            }
        }

        override fun onSecondaryCta(dialog: PreOnboardingDialogType) {
            when (dialog) {
                INITIAL_REINSTALL_USER -> {
                    _viewState.update { it.copy(isReinstallUser = true) }
                    viewModelScope.launch {
                        pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                        if (onboardingQuickSetupExperimentManager.enroll() == QuickSetupExperimentVariant.TREATMENT) {
                            showQuickSetupDialog()
                        } else {
                            setCurrentDialog(SKIP_ONBOARDING_OPTION)
                        }
                    }
                }

                SKIP_ONBOARDING_OPTION -> {
                    setCurrentDialog(COMPARISON_CHART, currentPageNumber = 1)
                    pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                }

                SYNC_RESTORE -> {
                    viewModelScope.launch {
                        logcat { "Sync-AutoRestore: user skipped restore" }
                        pixel.fire(PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE, type = Unique())
                        if (onboardingQuickSetupExperimentManager.enroll() == QuickSetupExperimentVariant.TREATMENT) {
                            showQuickSetupDialog()
                        } else {
                            setCurrentDialog(SKIP_ONBOARDING_OPTION)
                        }
                    }
                }

                INITIAL, COMPARISON_CHART, AI_COMPARISON_CHART, ADDRESS_BAR_POSITION, INPUT_SCREEN, INPUT_SCREEN_PREVIEW, QUICK_SETUP -> {
                    // no-op
                }
            }
        }

        override fun onInputModeDemoQuerySubmitted(query: String, isChat: Boolean) {
            viewModelScope.launch {
                if (isChat) {
                    _commands.send(Command.FinishAndSubmitChatPrompt(prompt = query))
                } else {
                    _commands.send(Command.FinishAndSubmitSearchQuery(query = query))
                }
            }
        }

        override fun onDefaultBrowserResult(isDefaultBrowser: Boolean) {
            viewModelScope.launch {
                _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
                setCurrentDialog(ADDRESS_BAR_POSITION, currentPageNumber = 2)
            }
        }
    }

    // Translates fragment callbacks into LinearOnboardingOrchestrator events and renders the orchestrator's
    // current step back onto the same viewState/commands the fragment already uses.
    private inner class OrchestratorFlow : OnboardingFlow {

        override fun start() {
            seedHasPlayedIntroAnimation()
            viewModelScope.launch {
                // Resolve the custom AI signal before collecting orchestrator state,
                // so the flag is set before any dialog is applied.
                // Custom AI plan gate requires orchestrator usage,
                // so this is okay to apply only to OrchestratorFlow and ignore for LegacyFlow.
                _viewState.update { it.copy(isCustomAiOnboardingFlow = customAiOnboardingStore.isEnabled()) }
                observeOrchestratorState()
            }
        }

        override fun onIntroAnimationFinished() = emit(NewUserOnboardingEvent.IntroAnimationFinished)

        // Legacy name: the fragment calls this once the notification-permission flow has finished
        // (requested from onIntroAnimationFinished) to load the next dax dialog. In orchestrator mode
        // the orchestrator owns which first dialog comes next via step preconditions, so this call
        // only signals that the notification_permission step is done -> Advance.
        override fun loadDaxDialog() = emit(NewUserOnboardingEvent.NotificationPermissionFinished)

        override fun onPrimaryCta(dialog: PreOnboardingDialogType) {
            when (dialog) {
                SYNC_RESTORE -> emit(NewUserOnboardingEvent.RestoreRequested)
                INITIAL, INITIAL_REINSTALL_USER, COMPARISON_CHART, AI_COMPARISON_CHART, INPUT_SCREEN_PREVIEW ->
                    emit(NewUserOnboardingEvent.ContinueClicked)
                SKIP_ONBOARDING_OPTION -> emit(NewUserOnboardingEvent.SkipConfirmed)
                ADDRESS_BAR_POSITION ->
                    emit(NewUserOnboardingEvent.AddressBarConfirmed(_viewState.value.selectedAddressBarPosition))
                INPUT_SCREEN ->
                    emit(NewUserOnboardingEvent.InputModeConfirmed(_viewState.value.inputScreenSelected))
                QUICK_SETUP -> {
                    val state = _viewState.value
                    emit(NewUserOnboardingEvent.QuickSetupConfirmed(state.selectedAddressBarPosition, state.inputScreenSelected))
                }
            }
        }

        override fun onSecondaryCta(dialog: PreOnboardingDialogType) {
            when (dialog) {
                INITIAL_REINSTALL_USER, SYNC_RESTORE -> emit(NewUserOnboardingEvent.SkipRequested)
                SKIP_ONBOARDING_OPTION -> emit(NewUserOnboardingEvent.ResumeRequested)
                INITIAL, COMPARISON_CHART, AI_COMPARISON_CHART, ADDRESS_BAR_POSITION, INPUT_SCREEN, INPUT_SCREEN_PREVIEW, QUICK_SETUP -> Unit
            }
        }

        // The step records the query into the run; the Completed handler reads it off state.result.
        override fun onInputModeDemoQuerySubmitted(query: String, isChat: Boolean) =
            emit(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = query, isChat = isChat))

        override fun onDefaultBrowserResult(isDefaultBrowser: Boolean) =
            emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = isDefaultBrowser))

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
        // Reuses setCurrentDialog / setInputScreenPreviewDialog so shown pixels fire exactly as in legacy.
        private suspend fun applyDialog(dialog: NewUserOnboardingActivityDialog, progress: StepProgress?) {
            // Step-indicator dialogs derive their "page N of M" from the plan-position progress; null = no indicator.
            val page = progress?.current
            val total = progress?.total ?: DEFAULT_STEP_COUNT
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
                    setCurrentDialog(COMPARISON_CHART, currentPageNumber = page, totalSteps = total)
                NewUserOnboardingActivityDialog.AiComparisonChart ->
                    setCurrentDialog(AI_COMPARISON_CHART, currentPageNumber = page, totalSteps = total)
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
                    setCurrentDialog(ADDRESS_BAR_POSITION, currentPageNumber = page, totalSteps = total)
                }
                NewUserOnboardingActivityDialog.InputScreen ->
                    setCurrentDialog(INPUT_SCREEN, currentPageNumber = page, totalSteps = total)
                is NewUserOnboardingActivityDialog.InputScreenPreview ->
                    setInputScreenPreviewDialog(isSearchDefault = dialog.isSearchDefault, currentPageNumber = page, totalSteps = total)
                NewUserOnboardingActivityDialog.SkipNewUserOnboardingOption -> setCurrentDialog(SKIP_ONBOARDING_OPTION)
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

    private companion object {
        private const val BLOCK_STORE_TIMEOUT_MS = 3_000L

        // Legacy flow renders a fixed 3-step indicator; the orchestrator flow overrides this per step from the
        // plan-derived total. Used as the default until an indicator step sets the real count.
        private const val DEFAULT_STEP_COUNT = 3
    }
}
