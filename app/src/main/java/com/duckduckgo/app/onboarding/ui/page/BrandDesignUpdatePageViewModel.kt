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
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant.CONTROL
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant.TREATMENT_WITH_DUCK_AI_DEFAULT
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant.TREATMENT_WITH_SEARCH_DEFAULT
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.*
import com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType.ADDRESS_BAR_POSITION
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
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_QUICK_SETUP
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
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val settingsDataStore: SettingsDataStore,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val onboardingStore: OnboardingStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val duckChat: DuckChat,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
    private val duckAiOnboardingExperimentManager: DuckAiOnboardingExperimentManager,
    private val onboardingQuickSetupExperimentManager: OnboardingQuickSetupExperimentManager,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val deviceInfo: DeviceInfo,
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
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var maxPageCount: Int = 2
    private var quickSetupDefaultBrowserDialogShown: Boolean = false

    init {
        viewModelScope.launch(dispatchers.io()) {
            maxPageCount = if (androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()) {
                3
            } else {
                2
            }
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

    private fun setCurrentDialog(dialogType: PreOnboardingDialogType) {
        _viewState.update { it.copy(currentDialog = dialogType, hasAnimatedCurrentDialog = false) }
        fireDialogShownPixel(dialogType)
    }

    private fun setInputScreenPreviewDialog(isSearchDefault: Boolean) {
        _viewState.update {
            it.copy(
                currentDialog = INPUT_SCREEN_PREVIEW,
                hasAnimatedCurrentDialog = false,
                inputScreenPreviewSearchSuggestions = onboardingStore.getSearchOptions(),
                inputScreenPreviewChatSuggestions = onboardingStore.getChatSuggestions(),
                inputScreenPreviewIsSearchSelected = isSearchDefault,
            )
        }
        fireDialogShownPixel(INPUT_SCREEN_PREVIEW)
    }

    private fun fireDialogShownPixel(dialogType: PreOnboardingDialogType) {
        when (dialogType) {
            SYNC_RESTORE -> {} // TODO - SyncRestore: add pixel for dialog shown
            INITIAL_REINSTALL_USER -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            INITIAL -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            COMPARISON_CHART -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            SKIP_ONBOARDING_OPTION -> pixel.fire(PREONBOARDING_SKIP_ONBOARDING_SHOWN_UNIQUE, type = Unique())
            ADDRESS_BAR_POSITION -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            INPUT_SCREEN -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
            INPUT_SCREEN_PREVIEW -> {
            }

            QUICK_SETUP -> {
                fireQuickSetupShownPixel()
            }
        }
    }

    fun onIntroAnimationFinished() {
        _viewState.update { it.copy(hasPlayedIntroAnimation = true) }
        viewModelScope.launch {
            delay(2.seconds)
            _commands.send(Command.RequestNotificationPermissions)
        }
    }

    fun loadDaxDialog() {
        viewModelScope.launch {
            val isReinstall = isAppReinstall()
            val dialogType = if (isReinstall) INITIAL_REINSTALL_USER else INITIAL
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

    fun onPrimaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            SYNC_RESTORE -> {
                // TODO - SyncRestore: handle primary CTA click
            }

            INITIAL_REINSTALL_USER, INITIAL -> {
                setCurrentDialog(COMPARISON_CHART)
            }

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
                                setCurrentDialog(ADDRESS_BAR_POSITION)
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
                    val showInputScreen = withContext(dispatchers.io()) {
                        androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()
                    }
                    if (showInputScreen) {
                        setCurrentDialog(INPUT_SCREEN)
                    } else {
                        _commands.send(Command.Finish)
                    }
                }
            }

            INPUT_SCREEN -> {
                viewModelScope.launch {
                    applyInputScreenSelection()
                    val inputSelected = _viewState.value.inputScreenSelected
                    if (inputSelected) {
                        when (duckAiOnboardingExperimentManager.enroll()) {
                            null,
                            CONTROL,
                            -> _commands.send(Command.Finish)

                            TREATMENT_WITH_DUCK_AI_DEFAULT -> setInputScreenPreviewDialog(isSearchDefault = false)
                            TREATMENT_WITH_SEARCH_DEFAULT -> setInputScreenPreviewDialog(isSearchDefault = true)
                        }
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
                    fireQuickSetupClickedPixel()
                    _commands.send(Command.OnboardingSkipped)
                }
            }
        }
    }

    fun onInputModeDemoQuerySubmitted(
        query: String,
        isChat: Boolean,
    ) {
        viewModelScope.launch {
            if (isChat) {
                _commands.send(Command.FinishAndSubmitChatPrompt(prompt = query))
            } else {
                _commands.send(Command.FinishAndSubmitSearchQuery(query = query))
            }
        }
    }

    fun onSecondaryCtaClicked() {
        val currentDialog = _viewState.value.currentDialog ?: return
        when (currentDialog) {
            INITIAL_REINSTALL_USER -> {
                _viewState.update { it.copy(isReinstallUser = true) }
                viewModelScope.launch {
                    if (onboardingQuickSetupExperimentManager.enroll() == QuickSetupExperimentVariant.TREATMENT) {
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
                    } else {
                        setCurrentDialog(SKIP_ONBOARDING_OPTION)
                        pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                    }
                }
            }

            SKIP_ONBOARDING_OPTION -> {
                setCurrentDialog(COMPARISON_CHART)
                pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
            }

            SYNC_RESTORE, INITIAL, COMPARISON_CHART, ADDRESS_BAR_POSITION, INPUT_SCREEN, INPUT_SCREEN_PREVIEW, QUICK_SETUP -> {
                // no-op
            }
        }
    }

    fun onDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true)
        viewModelScope.launch {
            _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
            setCurrentDialog(ADDRESS_BAR_POSITION)
        }
    }

    fun onDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false)
        viewModelScope.launch {
            _viewState.update { it.copy(showSplitOption = isSplitOmnibarEnabled()) }
            setCurrentDialog(ADDRESS_BAR_POSITION)
        }
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
    }

    fun notificationRuntimePermissionGranted() {
        pixel.fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    fun getMaxPageCount(): Int {
        return maxPageCount
    }

    private suspend fun isAppReinstall(): Boolean =
        withContext(dispatchers.io()) {
            appBuildConfig.isAppReinstall()
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
        duckChat.setCosmeticInputScreenUserSetting(inputSelected)
        onboardingStore.storeInputScreenSelection(inputSelected)
        if (fireTelemetry) {
            fireInputScreenSelectionTelemetry(inputSelected)
        }
    }

    private fun fireInputScreenSelectionTelemetry(inputSelected: Boolean) {
        if (inputSelected) {
            inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = _viewState.value.isReinstallUser)
            pixel.fire(PREONBOARDING_AICHAT_SELECTED)
        } else {
            pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
        }
    }

    private fun skipDialogAnimations() {
        viewModelScope.launch {
            _commands.send(Command.SkipDialogAnimation)
        }
    }

    private fun fireQuickSetupShownPixel() {
        viewModelScope.launch {
            val params = buildQuickSetupStandardParams() + (PIXEL_PARAM_EVENT to PIXEL_EVENT_SHOWN)
            pixel.fire(
                pixel = ONBOARDING_QUICK_SETUP,
                parameters = params,
                type = Unique(tag = "${ONBOARDING_QUICK_SETUP.pixelName}_$PIXEL_EVENT_SHOWN"),
            )
        }
    }

    private suspend fun fireQuickSetupClickedPixel() {
        val state = _viewState.value
        val (isDefault, hasWidget) = withContext(dispatchers.io()) {
            defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
        }
        val addressBar = when (state.selectedAddressBarPosition) {
            OmnibarType.SINGLE_TOP -> ADDRESS_BAR_TOP
            OmnibarType.SINGLE_BOTTOM -> ADDRESS_BAR_BOTTOM
            OmnibarType.SPLIT -> ADDRESS_BAR_SPLIT
        }
        val inputType = if (state.inputScreenSelected) {
            INPUT_TYPE_SEARCH_AND_DUCKAI
        } else {
            INPUT_TYPE_SEARCH
        }
        val value = "$PIXEL_SET_AS_DEFAULT_VALUE_PARAM:${onOff(isDefault)} " +
            "$PIXEL_WIDGET_VALUE_PARAM:${onOff(hasWidget)} " +
            "$PIXEL_ADDRESS_BAR_VALUE_PARAM:$addressBar " +
            "$PIXEL_INPUT_TYPE_VALUE_PARAM:$inputType"
        val params = buildQuickSetupStandardParams() + mapOf(
            PIXEL_PARAM_EVENT to PIXEL_EVENT_CLICKED,
            PIXEL_PARAM_VALUE to value,
        )
        pixel.fire(
            pixel = ONBOARDING_QUICK_SETUP,
            parameters = params,
            type = Unique(tag = "${ONBOARDING_QUICK_SETUP.pixelName}_$PIXEL_EVENT_CLICKED"),
        )
    }

    private suspend fun buildQuickSetupStandardParams(): Map<String, String> {
        val days = withContext(dispatchers.io()) { appInstallStore.daysInstalled() }
        val params = mutableMapOf(
            PIXEL_PARAM_INSTALL_TYPE to if (_viewState.value.isReinstallUser) INSTALL_TYPE_REINSTALL else INSTALL_TYPE_NEW,
            PIXEL_PARAM_SOURCE to ONBOARDING_DEFAULT,
            PIXEL_PARAM_FLOW to ONBOARDING_DEFAULT,
            PIXEL_PARAM_PIXEL_SOURCE to deviceInfo.formFactor().description,
        )
        if (days in 0..MAX_DAYS_SINCE_INSTALL_REPORTED) {
            params[PIXEL_PARAM_DAYS_SINCE_INSTALL] = days.toString()
        }
        return params
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private companion object {
        private const val PIXEL_PARAM_EVENT = "e"
        private const val PIXEL_PARAM_VALUE = "value"
        private const val PIXEL_SET_AS_DEFAULT_VALUE_PARAM = "set_as_default"
        private const val PIXEL_WIDGET_VALUE_PARAM = "widget"
        private const val PIXEL_ADDRESS_BAR_VALUE_PARAM = "address_bar"
        private const val PIXEL_INPUT_TYPE_VALUE_PARAM = "input_type"
        private const val PIXEL_PARAM_INSTALL_TYPE = "it"
        private const val PIXEL_PARAM_DAYS_SINCE_INSTALL = "d"
        private const val PIXEL_PARAM_SOURCE = "source"
        private const val PIXEL_PARAM_FLOW = "flow"
        private const val PIXEL_PARAM_PIXEL_SOURCE = "pixelSource"

        private const val PIXEL_EVENT_SHOWN = "shown"
        private const val PIXEL_EVENT_CLICKED = "clicked"

        private const val INSTALL_TYPE_NEW = "new"
        private const val INSTALL_TYPE_REINSTALL = "reinstall"

        private const val ONBOARDING_DEFAULT = "default"

        private const val ADDRESS_BAR_TOP = "top"
        private const val ADDRESS_BAR_BOTTOM = "bottom"
        private const val ADDRESS_BAR_SPLIT = "split"

        private const val INPUT_TYPE_SEARCH = "search"
        private const val INPUT_TYPE_SEARCH_AND_DUCKAI = "search_and_duckai"

        private const val MAX_DAYS_SINCE_INSTALL_REPORTED = 28L
    }
}
