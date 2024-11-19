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

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutScreenNoParams
import com.duckduckgo.app.accessibility.AccessibilityScreens
import com.duckduckgo.app.appearance.AppearanceScreen
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivitySettingsNewBinding
import com.duckduckgo.app.email.ui.EmailProtectionUnsupportedScreenNoParams
import com.duckduckgo.app.firebutton.FireButtonScreenNoParams
import com.duckduckgo.app.generalsettings.GeneralSettingsScreenNoParams
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.permissions.PermissionsScreenNoParams
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.PRIVACY_PRO_IS_ENABLED_AND_ELIGIBLE
import com.duckduckgo.app.privatesearch.PrivateSearchScreenNoParams
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.webtrackingprotection.WebTrackingProtectionScreenNoParams
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
import com.duckduckgo.browser.api.ui.BrowserScreens.NewSettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.CheckListItem
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.macos.api.MacOsScreenWithEmptyParams
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.settings.api.DuckPlayerSettingsPlugin
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.windows.api.ui.WindowsScreenWithEmptyParams
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber


@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NewSettingsScreenNoParams::class, screenName = "newSettings")
class NewSettingsActivity : DuckDuckGoActivity() {

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

    private val viewsPrivacy
        get() = binding.includeSettings.contentSettingsPrivacy

    private val viewsSettings
        get() = binding.includeSettings.contentSettingsSettings

    private val viewsMore
        get() = binding.includeSettings.contentSettingsMore

    private val viewsInternal
        get() = binding.includeSettings.contentSettingsInternal

    private val viewsPro
        get() = binding.includeSettings.settingsSectionPro

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureInternalFeatures()
        configureSettings()
        lifecycle.addObserver(viewModel)
        observeViewModel()

        intent?.getStringExtra(BrowserActivity.LAUNCH_FROM_NOTIFICATION_PIXEL_NAME)?.let {
            viewModel.onLaunchedFromNotification(it)
        }
    }

    private fun configureUiEventHandlers() {
        with(viewsPrivacy) {
            setAsDefaultBrowserSetting.setClickListener { viewModel.onDefaultBrowserSettingClicked() }
            privateSearchSetting.setClickListener { viewModel.onPrivateSearchSettingClicked() }
            webTrackingProtectionSetting.setClickListener { viewModel.onWebTrackingProtectionSettingClicked() }
            cookiePopupProtectionSetting.setClickListener { viewModel.onCookiePopupProtectionSettingClicked() }
            emailSetting.setClickListener { viewModel.onEmailProtectionSettingClicked() }
            vpnSetting.setClickListener { viewModel.onAppTPSettingClicked() }
        }

        with(viewsSettings) {
            homeScreenWidgetSetting.setClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
            autofillLoginsSetting.setClickListener { viewModel.onAutofillSettingsClick() }
            syncSetting.setClickListener { viewModel.onSyncSettingClicked() }
            fireButtonSetting.setClickListener { viewModel.onFireButtonSettingClicked() }
            permissionsSetting.setClickListener { viewModel.onPermissionsSettingClicked() }
            appearanceSetting.setClickListener { viewModel.onAppearanceSettingClicked() }
            accessibilitySetting.setClickListener { viewModel.onAccessibilitySettingClicked() }
            aboutSetting.setClickListener { viewModel.onAboutSettingClicked() }
            generalSetting.setClickListener { viewModel.onGeneralSettingClicked() }
        }

        with(viewsMore) {
            macOsSetting.setClickListener { viewModel.onMacOsSettingClicked() }
            windowsSetting.setClickListener { viewModel.windowsSettingClicked() }
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
            viewsSettings.settingsSectionDuckPlayer.gone()
        } else {
            duckPlayerSettingsPlugin.forEach { plugin ->
                viewsSettings.settingsSectionDuckPlayer.addView(plugin.getView(this))
            }
        }
    }

    private fun configureInternalFeatures() {
        viewsInternal.settingsSectionInternal.visibility = if (internalFeaturePlugins.getPlugins().isEmpty()) View.GONE else View.VISIBLE
        internalFeaturePlugins.getPlugins().forEach { feature ->
            Timber.v("Adding internal feature ${feature.internalFeatureTitle()}")
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
                        it.appTrackingProtectionOnboardingShown,
                    )
                    updateEmailSubtitle(it.emailAddress)
                    updateAutofill(it.showAutofill)
                    updateSyncSetting(visible = it.showSyncSetting)
                    updateAutoconsent(it.isAutoconsentEnabled)
                    updatePrivacyPro(it.isPrivacyProEnabled)
                    updateDuckPlayer(it.isDuckPlayerEnabled)
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
            viewsSettings.settingsSectionDuckPlayer.show()
        } else {
            viewsSettings.settingsSectionDuckPlayer.gone()
        }
    }

    private fun updateAutofill(autofillEnabled: Boolean) = with(viewsSettings.autofillLoginsSetting) {
        visibility = if (autofillEnabled) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateEmailSubtitle(emailAddress: String?) {
        if (emailAddress.isNullOrEmpty()) {
            viewsPrivacy.emailSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.settingsEmailProtectionSubtitle))
            viewsPrivacy.emailSetting.setItemStatus(CheckListItem.CheckItemStatus.DISABLED)
        } else {
            viewsPrivacy.emailSetting.setSecondaryText(emailAddress)
            viewsPrivacy.emailSetting.setItemStatus(CheckListItem.CheckItemStatus.ENABLED)
        }
    }

    private fun updateSyncSetting(visible: Boolean) {
        with(viewsSettings.syncSetting) {
            isVisible = visible
        }
    }

    private fun updateAutoconsent(enabled: Boolean) {
        if (enabled) {
            viewsPrivacy.cookiePopupProtectionSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.cookiePopupProtectionEnabled))
            viewsPrivacy.cookiePopupProtectionSetting.setItemStatus(CheckListItem.CheckItemStatus.ENABLED)
        } else {
            viewsPrivacy.cookiePopupProtectionSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.cookiePopupProtectionDescription))
            viewsPrivacy.cookiePopupProtectionSetting.setItemStatus(CheckListItem.CheckItemStatus.DISABLED)
        }
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.LaunchDefaultBrowser -> launchDefaultAppScreen()
            is Command.LaunchAutofillSettings -> launchAutofillSettings()
            is Command.LaunchAccessibilitySettings -> launchAccessibilitySettings()
            is Command.LaunchAppTPTrackersScreen -> launchAppTPTrackersScreen()
            is Command.LaunchAppTPOnboarding -> launchAppTPOnboardingScreen()
            is Command.LaunchEmailProtection -> launchEmailProtectionScreen(it.url)
            is Command.LaunchEmailProtectionNotSupported -> launchEmailProtectionNotSupported()
            is Command.LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget()
            is Command.LaunchMacOs -> launchMacOsScreen()
            is Command.LaunchWindows -> launchWindowsScreen()
            is Command.LaunchSyncSettings -> launchSyncSettings()
            is Command.LaunchPrivateSearchWebPage -> launchPrivateSearchScreen()
            is Command.LaunchWebTrackingProtectionScreen -> launchWebTrackingProtectionScreen()
            is Command.LaunchCookiePopupProtectionScreen -> launchCookiePopupProtectionScreen()
            is Command.LaunchFireButtonScreen -> launchFireButtonScreen()
            is Command.LaunchPermissionsScreen -> launchPermissionsScreen()
            is Command.LaunchAppearanceScreen -> launchAppearanceScreen()
            is Command.LaunchAboutScreen -> launchAboutScreen()
            is Command.LaunchGeneralSettingsScreen -> launchGeneralSettingsScreen()
            null -> TODO()
        }
    }

    private fun updateDefaultBrowserViewVisibility(it: SettingsViewModel.ViewState) {
        with(viewsPrivacy.setAsDefaultBrowserSetting) {
            visibility = if (it.showDefaultBrowserSetting) {
                if (it.isAppDefaultBrowser) {
                    setItemStatus(CheckListItem.CheckItemStatus.ENABLED)
                    setSecondaryText(getString(com.duckduckgo.app.browser.R.string.settingsDefaultBrowserSetDescription))
                } else {
                    setItemStatus(CheckListItem.CheckItemStatus.DISABLED)
                    setSecondaryText(getString(com.duckduckgo.app.browser.R.string.settingsDefaultBrowserNotSetDescription))
                }
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun updateDeviceShieldSettings(
        appTPEnabled: Boolean,
        appTrackingProtectionOnboardingShown: Boolean,
    ) {
        with(viewsPrivacy) {
            if (appTPEnabled) {
                vpnSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.atp_SettingsDeviceShieldEnabled))
                vpnSetting.setItemStatus(CheckListItem.CheckItemStatus.ENABLED)
            } else {
                if (appTrackingProtectionOnboardingShown) {
                    vpnSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.atp_SettingsDeviceShieldDisabled))
                    vpnSetting.setItemStatus(CheckListItem.CheckItemStatus.WARNING)
                } else {
                    vpnSetting.setSecondaryText(getString(com.duckduckgo.app.browser.R.string.atp_SettingsDeviceShieldNeverEnabled))
                    vpnSetting.setItemStatus(CheckListItem.CheckItemStatus.DISABLED)
                }
            }
        }
    }

    private fun launchDefaultAppScreen() {
        launchDefaultAppActivity()
    }

    private fun launchAutofillSettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AutofillSettingsScreen(source = AutofillSettingsLaunchSource.SettingsActivity), options)
    }

    private fun launchAccessibilitySettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AccessibilityScreens.Default, options)
    }

    private fun launchEmailProtectionScreen(url: String) {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(BrowserActivity.intent(this, url, interstitialScreen = true), options)
        this.finish()
    }

    private fun launchEmailProtectionNotSupported() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, EmailProtectionUnsupportedScreenNoParams, options)
    }

    private fun launchMacOsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, MacOsScreenWithEmptyParams, options)
    }

    private fun launchWindowsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, WindowsScreenWithEmptyParams, options)
    }

    private fun launchSyncSettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, SyncActivityWithEmptyParams, options)
    }

    private fun launchAppTPTrackersScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AppTrackerActivityWithEmptyParams, options)
    }

    private fun launchAppTPOnboardingScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AppTrackerOnboardingActivityWithEmptyParamsParams, options)
    }

    private fun launchAddHomeScreenWidget() {
        pixel.fire(AppPixelName.SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED)
        addWidgetLauncher.launchAddWidget(this)
    }

    private fun launchPrivateSearchScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, PrivateSearchScreenNoParams, options)
    }

    private fun launchWebTrackingProtectionScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, WebTrackingProtectionScreenNoParams, options)
    }

    private fun launchCookiePopupProtectionScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(AutoconsentSettingsActivity.intent(this), options)
    }

    private fun launchFireButtonScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, FireButtonScreenNoParams, options)
    }

    private fun launchPermissionsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, PermissionsScreenNoParams, options)
    }

    private fun launchAppearanceScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AppearanceScreen.Default, options)
    }

    private fun launchAboutScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, AboutScreenNoParams, options)
    }

    private fun launchGeneralSettingsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, GeneralSettingsScreenNoParams, options)
    }

    companion object {
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"

        fun intent(context: Context): Intent {
            return Intent(context, NewSettingsActivity::class.java)
        }
    }
}
