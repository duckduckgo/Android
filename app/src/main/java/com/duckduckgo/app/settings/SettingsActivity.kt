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

package com.duckduckgo.app.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutScreenNoParams
import com.duckduckgo.app.about.FeedbackContract
import com.duckduckgo.app.accessibility.AccessibilityScreens
import com.duckduckgo.app.appearance.AppearanceScreen
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivitySettingsNewBinding
import com.duckduckgo.app.email.ui.EmailProtectionUnsupportedScreenNoParams
import com.duckduckgo.app.firebutton.FireButtonScreenNoParams
import com.duckduckgo.app.generalsettings.GeneralSettingsScreenNoParams
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.permissions.PermissionsScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_PRO_IS_ENABLED_AND_ELIGIBLE
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAboutScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAccessibilitySettings
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAppTPTrackersScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAutofillPasswordsManagement
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchAutofillSettings
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchCookiePopupProtectionScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchDuckChatScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchEmailProtectionNotSupported
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchGeneralSettingsScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchOtherPlatforms
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchPermissionsScreen
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchPproUnifiedFeedback
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchPrivateSearchWebPage
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchSyncSettings
import com.duckduckgo.app.settings.SettingsViewModel.Command.LaunchWebTrackingProtectionScreen
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.webtrackingprotection.WebTrackingProtectionScreenNoParams
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem.IconSize.Small
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.settings.api.CompleteSetupSettingsPlugin
import com.duckduckgo.settings.api.DuckPlayerSettingsPlugin
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.settings.api.ThreatProtectionSettingsPlugin
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.VERBOSE
import logcat.logcat

private const val OTHER_PLATFORMS_URL = "https://duckduckgo.com/app"

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SettingsScreenNoParams::class, screenName = "settings")
class SettingsActivity : DuckDuckGoActivity() {

    private val viewModel: SettingsViewModel by bindViewModel()
    private val binding: ActivitySettingsNewBinding by viewBinding()

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var internalFeaturePlugins: PluginPoint<InternalFeaturePlugin>

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var _proSettingsPlugin: PluginPoint<ProSettingsPlugin>
    private val proSettingsPlugin by lazy {
        _proSettingsPlugin.getPlugins()
    }

    @Inject
    lateinit var _duckPlayerSettingsPlugin: PluginPoint<DuckPlayerSettingsPlugin>
    private val duckPlayerSettingsPlugin by lazy {
        _duckPlayerSettingsPlugin.getPlugins()
    }

    @Inject
    lateinit var _threatProtectionSettingsPlugin: PluginPoint<ThreatProtectionSettingsPlugin>
    private val threatProtectionSettingsPlugin by lazy {
        _threatProtectionSettingsPlugin.getPlugins()
    }

    @Inject
    lateinit var _completeSetupSettingsPlugin: PluginPoint<CompleteSetupSettingsPlugin>
    private val completeSetupSettingsPlugin by lazy {
        _completeSetupSettingsPlugin.getPlugins()
    }

    private val feedbackFlow = registerForActivityResult(FeedbackContract()) { resultOk ->
        if (resultOk) {
            Snackbar.make(
                binding.root,
                R.string.thanksForTheFeedback,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    private val viewsPrivacy
        get() = binding.includeSettings.contentSettingsPrivacy

    private val viewsMain
        get() = binding.includeSettings.contentSettingsMain

    private val viewsNextSteps
        get() = binding.includeSettings.contentSettingsNextSteps

    private val viewsOther
        get() = binding.includeSettings.contentSettingsOther

    private val viewsInternal
        get() = binding.includeSettings.contentSettingsInternal

    private val viewsPro
        get() = binding.includeSettings.contentSettingsPrivacyPro

    private val viewsCompleteSetup
        get() = binding.includeSettings.contentSettingsCompleteSetup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureInternalFeatures()
        configureSettings()
        configureCompleteSetupSettings()
        lifecycle.addObserver(viewModel)
        observeViewModel()
    }

    private fun configureCompleteSetupSettings() {
        watchForCompleteSetupSettingsChanges()
        updateCompleteSetupSettings()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWidgetsInstalledState()
    }

    private fun configureUiEventHandlers() {
        with(viewsPrivacy) {
            setAsDefaultBrowserSetting.setClickListener { viewModel.onDefaultBrowserSettingClicked() }
            privateSearchSetting.setClickListener { viewModel.onPrivateSearchSettingClicked() }
            webTrackingProtectionSetting.setClickListener { viewModel.onWebTrackingProtectionSettingClicked() }
            cookiePopupProtectionSetting.setClickListener { viewModel.onCookiePopupProtectionSettingClicked() }
            emailSetting.setClickListener { viewModel.onEmailProtectionSettingClicked() }
            vpnSetting.setClickListener { viewModel.onAppTPSettingClicked() }
            widgetPromptSetting.setOnClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
        }

        with(viewsMain) {
            autofillLoginsSetting.setClickListener { viewModel.onAutofillSettingsClick() }
            syncSetting.setClickListener { viewModel.onSyncSettingClicked() }
            fireButtonSetting.setClickListener { viewModel.onFireButtonSettingClicked() }
            permissionsSetting.setClickListener { viewModel.onPermissionsSettingClicked() }
            appearanceSetting.setClickListener { viewModel.onAppearanceSettingClicked() }
            accessibilitySetting.setClickListener { viewModel.onAccessibilitySettingClicked() }
            generalSetting.setClickListener { viewModel.onGeneralSettingClicked() }
            includeDuckChatSetting.duckChatSetting.setOnClickListener { viewModel.onDuckChatSettingClicked() }
        }

        with(viewsNextSteps) {
            addWidgetToHomeScreenSetting.setOnClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
            addressBarPositionSetting.setOnClickListener { viewModel.onChangeAddressBarPositionClicked() }
            enableVoiceSearchSetting.setOnClickListener { viewModel.onEnableVoiceSearchClicked() }
        }

        with(viewsOther) {
            aboutSetting.setOnClickListener { viewModel.onAboutSettingClicked() }
            shareFeedbackSetting.setOnClickListener { viewModel.onShareFeedbackClicked() }
            ddgOnOtherPlatformsSetting.setTrailingIconSize(Small)
            ddgOnOtherPlatformsSetting.setOnClickListener { viewModel.onDdgOnOtherPlatformsClicked() }
        }
    }

    private fun configureSettings() {
        if (proSettingsPlugin.isEmpty()) {
            viewsPro.gone()
        } else {
            proSettingsPlugin.forEach { plugin ->
                viewsPro.addView(plugin.getView(this))
            }
        }

        if (duckPlayerSettingsPlugin.isEmpty()) {
            viewsMain.settingsSectionDuckPlayer.gone()
        } else {
            duckPlayerSettingsPlugin.forEach { plugin ->
                viewsMain.settingsSectionDuckPlayer.addView(plugin.getView(this))
            }
        }

        if (threatProtectionSettingsPlugin.isEmpty()) {
            viewsPrivacy.settingsSectionThreatProtection.gone()
        } else {
            threatProtectionSettingsPlugin.forEach { plugin ->
                viewsPrivacy.settingsSectionThreatProtection.addView(plugin.getView(this))
            }
        }
    }

    private fun configureInternalFeatures() {
        viewsInternal.settingsSectionInternal.visibility = if (internalFeaturePlugins.getPlugins().isEmpty()) View.GONE else View.VISIBLE
        internalFeaturePlugins.getPlugins().forEach { feature ->
            logcat(VERBOSE) { "Adding internal feature ${feature.internalFeatureTitle()}" }
            val view = TwoLineListItem(this).apply {
                setPrimaryText(feature.internalFeatureTitle())
                setSecondaryText(feature.internalFeatureSubtitle())
            }
            viewsInternal.settingsInternalFeaturesContainer.addView(view)
            view.setClickListener { feature.onInternalFeatureClicked(this) }
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .distinctUntilChanged()
            .onEach { viewState ->
                viewState.let {
                    updateDefaultBrowserViewVisibility(it)
                    updateDeviceShieldSettings(
                        it.appTrackingProtectionEnabled,
                    )
                    updateEmailSubtitle(it.emailAddress)
                    updateAutofill(it.showAutofill)
                    updateSyncSetting(visible = it.showSyncSetting)
                    updateAutoconsent(it.isAutoconsentEnabled)
                    updatePrivacyPro(it.isPrivacyProEnabled)
                    updateDuckPlayer(it.isDuckPlayerEnabled)
                    updateThreatProtection(it.isNewThreatProtectionSettingsEnabled)
                    updateDuckChat(it.isDuckChatEnabled)
                    updateVoiceSearchVisibility(it.isVoiceSearchVisible)
                    updateAddWidgetInProtections(it.isAddWidgetInProtectionsVisible, it.widgetsInstalled)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updatePrivacyPro(isPrivacyProEnabled: Boolean) {
        if (isPrivacyProEnabled) {
            pixel.fire(PRIVACY_PRO_IS_ENABLED_AND_ELIGIBLE, type = Daily())
            viewsPro.show()
        } else {
            viewsPro.gone()
        }
    }

    private fun updateDuckPlayer(isDuckPlayerEnabled: Boolean) {
        if (isDuckPlayerEnabled) {
            viewsMain.settingsSectionDuckPlayer.show()
        } else {
            viewsMain.settingsSectionDuckPlayer.gone()
        }
    }

    private fun updateThreatProtection(newThreatProtectionSettingsEnabled: Boolean) {
        if (newThreatProtectionSettingsEnabled) {
            viewsPrivacy.settingsSectionThreatProtection.show()
        } else {
            viewsPrivacy.settingsSectionThreatProtection.gone()
        }
    }

    private fun updateDuckChat(isDuckChatEnabled: Boolean) {
        if (isDuckChatEnabled) {
            viewsMain.includeDuckChatSetting.duckChatSetting.show()
        } else {
            viewsMain.includeDuckChatSetting.duckChatSetting.gone()
        }
    }

    private fun updateVoiceSearchVisibility(isVisible: Boolean) {
        viewsNextSteps.enableVoiceSearchSetting.isVisible = isVisible
    }

    private fun updateAddWidgetInProtections(isVisible: Boolean, widgetsInstalled: Boolean) {
        if (isVisible) {
            viewsPrivacy.widgetPromptSetting.setStatus(isOn = widgetsInstalled)
        }
        viewsPrivacy.widgetPromptSetting.isVisible = isVisible
        viewsNextSteps.addWidgetToHomeScreenSetting.isVisible = !isVisible
    }

    private fun watchForCompleteSetupSettingsChanges() {
        with(viewsCompleteSetup) {
            settingsCompleteFeaturesContainer.viewTreeObserver.addOnGlobalLayoutListener {
                if (settingsCompleteFeaturesContainer.children.any { it.isVisible }) {
                    settingsSectionCompleteSetup.show()
                } else {
                    settingsSectionCompleteSetup.gone()
                }
            }
        }
    }

    private fun updateCompleteSetupSettings() {
        val viewsToInclude = completeSetupSettingsPlugin.map { it.getView(this) }

        with(viewsCompleteSetup.settingsCompleteFeaturesContainer) {
            removeAllViews()
            viewsToInclude.forEach { addView(it) }
        }
    }

    private fun updateAutofill(autofillEnabled: Boolean) = with(viewsMain.autofillLoginsSetting) {
        visibility = if (autofillEnabled) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateEmailSubtitle(emailAddress: String?) {
        viewsPrivacy.emailSetting.setStatus(isOn = !emailAddress.isNullOrEmpty())
    }

    private fun updateSyncSetting(visible: Boolean) {
        with(viewsMain.syncSetting) {
            isVisible = visible
        }
    }

    private fun updateAutoconsent(enabled: Boolean) {
        viewsPrivacy.cookiePopupProtectionSetting.setStatus(isOn = enabled)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is LaunchDefaultBrowser -> launchDefaultAppScreen()
            is LaunchAutofillSettings -> launchScreen(AutofillSettingsScreen(source = AutofillScreenLaunchSource.SettingsActivity))
            is LaunchAutofillPasswordsManagement -> launchScreen(
                AutofillPasswordsManagementScreen(source = AutofillScreenLaunchSource.SettingsActivity),
            )
            is LaunchAccessibilitySettings -> launchScreen(AccessibilityScreens.Default)
            is LaunchAppTPTrackersScreen -> launchScreen(AppTrackerActivityWithEmptyParams)
            is LaunchAppTPOnboarding -> launchScreen(AppTrackerOnboardingActivityWithEmptyParamsParams)
            is LaunchEmailProtection -> launchActivityAndFinish(BrowserActivity.intent(this, it.url, interstitialScreen = true))
            is LaunchEmailProtectionNotSupported -> launchScreen(EmailProtectionUnsupportedScreenNoParams)
            is LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget(it.simpleWidgetPrompt)
            is LaunchSyncSettings -> launchScreen(SyncActivityWithEmptyParams)
            is LaunchPrivateSearchWebPage -> launchScreen(PrivateSearchScreenNoParams)
            is LaunchWebTrackingProtectionScreen -> launchScreen(WebTrackingProtectionScreenNoParams)
            is LaunchCookiePopupProtectionScreen -> launchActivity(AutoconsentSettingsActivity.intent(this))
            is LaunchFireButtonScreen -> launchScreen(FireButtonScreenNoParams)
            is LaunchPermissionsScreen -> launchScreen(PermissionsScreenNoParams)
            is LaunchDuckChatScreen -> launchScreen(DuckChatSettingsNoParams)
            is LaunchAppearanceScreen -> launchScreen(AppearanceScreen.Default)
            is LaunchAboutScreen -> launchScreen(AboutScreenNoParams)
            is LaunchGeneralSettingsScreen -> launchScreen(GeneralSettingsScreenNoParams)
            is LaunchFeedback -> launchFeedback()
            is LaunchPproUnifiedFeedback -> launchScreen(GeneralPrivacyProFeedbackScreenNoParams)
            is LaunchOtherPlatforms -> launchActivityAndFinish(BrowserActivity.intent(context = this, queryExtra = OTHER_PLATFORMS_URL))
        }
    }

    private fun updateDefaultBrowserViewVisibility(it: SettingsViewModel.ViewState) {
        with(viewsPrivacy.setAsDefaultBrowserSetting) {
            visibility = if (it.showDefaultBrowserSetting) {
                setStatus(isOn = it.isAppDefaultBrowser)
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun updateDeviceShieldSettings(appTPEnabled: Boolean) {
        viewsPrivacy.vpnSetting.setStatus(isOn = appTPEnabled)
    }

    private fun launchDefaultAppScreen() {
        launchDefaultAppActivity()
    }

    private fun launchScreen(activityParams: ActivityParams) {
        globalActivityStarter.start(this, activityParams)
    }

    private fun launchActivity(intent: Intent) {
        startActivity(intent)
    }

    private fun launchActivityAndFinish(intent: Intent) {
        launchActivity(intent)
        finish()
    }

    private fun launchAddHomeScreenWidget(simpleWidgetPrompt: Boolean) {
        pixel.fire(AppPixelName.SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED)
        addWidgetLauncher.launchAddWidget(this, simpleWidgetPrompt)
    }

    private fun launchFeedback() {
        feedbackFlow.launch(null)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
