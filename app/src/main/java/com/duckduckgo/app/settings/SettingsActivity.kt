/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.accessibility.AccessibilityActivity
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivitySettingsBinding
import com.duckduckgo.app.browser.webview.WebViewActivity
import com.duckduckgo.app.email.ui.EmailProtectionUnsupportedActivity
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.icon.ui.ChangeIconActivity
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.settings.SettingsViewModel.AutomaticallyClearData
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract.getAnimationForIndex
import com.duckduckgo.app.settings.clear.FireAnimation.None
import com.duckduckgo.app.settings.clear.getAppLinkSettingForIndex
import com.duckduckgo.app.settings.clear.getClearWhatOptionForIndex
import com.duckduckgo.app.settings.clear.getClearWhenForIndex
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.app.sitepermissions.SitePermissionsActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
import com.duckduckgo.autofill.api.AutofillSettingsActivityLauncher
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.macos_impl.MacOsActivity
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.DARK
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.LIGHT
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme.SYSTEM_DEFAULT
import com.duckduckgo.mobile.android.ui.sendThemeChangedBroadcast
import com.duckduckgo.mobile.android.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.listitem.TwoLineListItem
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnOnboardingActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.duckduckgo.windows.api.WindowsWaitlistState
import com.duckduckgo.windows.api.WindowsWaitlistState.InBeta
import com.duckduckgo.windows.api.WindowsWaitlistState.JoinedWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistState.NotJoinedQueue
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class SettingsActivity : DuckDuckGoActivity() {

    private val viewModel: SettingsViewModel by bindViewModel()
    private val binding: ActivitySettingsBinding by viewBinding()

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var internalFeaturePlugins: PluginPoint<InternalFeaturePlugin>

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var autofillSettingsActivityLauncher: AutofillSettingsActivityLauncher

    private val defaultBrowserChangeListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onDefaultBrowserToggled(isChecked)
    }

    private val autocompleteToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    private val viewsGeneral
        get() = binding.includeSettings.contentSettingsGeneral

    private val viewsAutofill
        get() = binding.includeSettings.contentSettingsAutofill

    private val viewsAppearance
        get() = binding.includeSettings.contentSettingsAppearance

    private val viewsPrivacy
        get() = binding.includeSettings.contentSettingsPrivacy

    private val viewsCustomize
        get() = binding.includeSettings.contentSettingsCustomize

    private val viewsInternal
        get() = binding.includeSettings.contentSettingsInternal

    private val viewsMore
        get() = binding.includeSettings.contentSettingsMore

    private val viewsOther
        get() = binding.includeSettings.contentSettingsOther

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureInternalFeatures()
        configureAppLinksSettingVisibility()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()

        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        viewModel.start(notificationsEnabled)
        viewModel.startPollingAppTpEnableState()
    }

    private fun configureUiEventHandlers() {
        with(viewsGeneral) {
            setAsDefaultBrowserSetting.setOnCheckedChangeListener(defaultBrowserChangeListener)
            homeScreenWidgetSetting.setClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
        }

        with(viewsAutofill) {
            autofill.setClickListener { viewModel.onAutofillSettingsClick() }
        }

        with(viewsAppearance) {
            selectedThemeSetting.setClickListener { viewModel.userRequestedToChangeTheme() }
            changeAppIconSetting.setOnClickListener { viewModel.userRequestedToChangeIcon() }
            selectedFireAnimationSetting.setClickListener { viewModel.userRequestedToChangeFireAnimation() }
            accessibilitySetting.setClickListener { viewModel.onAccessibilitySettingClicked() }
        }

        with(viewsPrivacy) {
            globalPrivacyControlSetting.setClickListener { viewModel.onGlobalPrivacyControlClicked() }
            autoconsentSetting.setClickListener { viewModel.onAutoconsentClicked() }
            fireproofWebsites.setClickListener { viewModel.onFireproofWebsitesClicked() }
            automaticallyClearWhatSetting.setClickListener { viewModel.onAutomaticallyClearWhatClicked() }
            automaticallyClearWhenSetting.setClickListener { viewModel.onAutomaticallyClearWhenClicked() }
            whitelist.setClickListener { viewModel.onManageWhitelistSelected() }
        }

        with(viewsCustomize) {
            autocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
            sitePermissions.setClickListener { viewModel.onSitePermissionsClicked() }
            notificationsSetting.setClickListener { viewModel.userRequestedToChangeNotificationsSetting() }
            appLinksSetting.setClickListener { viewModel.userRequestedToChangeAppLinkSetting() }
        }

        with(viewsOther) {
            provideFeedback.setClickListener { viewModel.userRequestedToSendFeedback() }
            about.setClickListener { startActivity(AboutDuckDuckGoActivity.intent(this@SettingsActivity)) }
            privacyPolicy.setClickListener {
                startActivity(
                    WebViewActivity.intent(
                        this@SettingsActivity,
                        PRIVACY_POLICY_WEB_LINK,
                        getString(R.string.settingsPrivacyPolicyDuckduckgo),
                    ),
                )
            }
        }

        with(viewsMore) {
            emailSetting.setClickListener { viewModel.onEmailProtectionSettingClicked() }
            macOsSetting.setClickListener { viewModel.onMacOsSettingClicked() }
            vpnSetting.setClickListener { viewModel.onAppTPSettingClicked() }
            windowsSetting.setClickListener { viewModel.windowsSettingClicked() }
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

    private fun configureAppLinksSettingVisibility() {
        if (appBuildConfig.sdkInt < Build.VERSION_CODES.N) {
            viewsCustomize.appLinksSetting.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    viewsOther.version.setSecondaryText(it.version)
                    updateSelectedTheme(it.theme)
                    viewsCustomize.autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                    updateDefaultBrowserViewVisibility(it)
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    setGlobalPrivacyControlSetting(it.globalPrivacyControlEnabled)
                    viewsAppearance.changeAppIcon.setImageResource(it.appIcon.icon)
                    setAutoconsentSetting(it.autoconsentEnabled)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                    updateAppLinkBehavior(it.appLinksSettingType)
                    updateDeviceShieldSettings(
                        it.appTrackingProtectionEnabled,
                        it.appTrackingProtectionOnboardingShown,
                    )
                    updateEmailSubtitle(it.emailAddress)
                    updateAutofill(it.showAutofill)
                    updateWindowsSettings(it.windowsWaitlistState)
                    viewsCustomize.notificationsSetting.setSecondaryText(getString(it.notificationsSettingSubtitleId))
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAutofill(autofillEnabled: Boolean) = with(viewsAutofill.settingsSectionAutofill) {
        visibility = if (autofillEnabled) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateWindowsSettings(waitlistState: WindowsWaitlistState?) {
        viewsMore.windowsSetting.isVisible = waitlistState != null

        with(viewsMore) {
            when (waitlistState) {
                is InBeta -> windowsSetting.setSecondaryText(getString(R.string.windows_settings_description_ready))
                is JoinedWaitlist -> windowsSetting.setSecondaryText(getString(R.string.windows_settings_description_list))
                is NotJoinedQueue -> windowsSetting.setSecondaryText(getString(R.string.windows_settings_description))
                null -> {}
            }
        }
    }

    private fun updateEmailSubtitle(emailAddress: String?) {
        val subtitle = emailAddress ?: getString(R.string.settingsEmailProtectionSubtitle)
        viewsMore.emailSetting.setSecondaryText(subtitle)
    }

    private fun setGlobalPrivacyControlSetting(enabled: Boolean) {
        val stateText = if (enabled) {
            getString(R.string.enabled)
        } else {
            getString(R.string.disabled)
        }
        viewsPrivacy.globalPrivacyControlSetting.setSecondaryText(stateText)
    }

    private fun setAutoconsentSetting(enabled: Boolean) {
        val stateText = if (enabled) {
            getString(R.string.enabled)
        } else {
            getString(R.string.disabled)
        }
        viewsPrivacy.autoconsentSetting.setSecondaryText(stateText)
    }

    private fun updateSelectedFireAnimation(fireAnimation: FireAnimation) {
        val subtitle = getString(fireAnimation.nameResId)
        viewsAppearance.selectedFireAnimationSetting.setSecondaryText(subtitle)
    }

    private fun updateSelectedTheme(selectedTheme: DuckDuckGoTheme) {
        val subtitle = getString(
            when (selectedTheme) {
                DARK -> R.string.settingsDarkTheme
                LIGHT -> R.string.settingsLightTheme
                SYSTEM_DEFAULT -> R.string.settingsSystemTheme
            },
        )
        viewsAppearance.selectedThemeSetting.setSecondaryText(subtitle)
    }

    private fun updateAppLinkBehavior(appLinkSettingType: AppLinkSettingType) {
        val subtitle = getString(
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> R.string.settingsAppLinksAskEveryTime
                AppLinkSettingType.ALWAYS -> R.string.settingsAppLinksAlways
                AppLinkSettingType.NEVER -> R.string.settingsAppLinksNever
            },
        )
        viewsCustomize.appLinksSetting.setSecondaryText(subtitle)
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhatSetting.setSecondaryText(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhenSetting.setSecondaryText(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        viewsPrivacy.automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    private fun launchAutomaticallyClearWhatDialog(option: ClearWhatOption) {
        val currentOption = option.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsAutomaticallyClearWhatDialogTitle)
            .setOptions(
                listOf(
                    R.string.settingsAutomaticallyClearWhatOptionNone,
                    R.string.settingsAutomaticallyClearWhatOptionTabs,
                    R.string.settingsAutomaticallyClearWhatOptionTabsAndData,
                ),
                currentOption,
            )
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val clearWhatSelected = selectedItem.getClearWhatOptionForIndex()
                        viewModel.onAutomaticallyWhatOptionSelected(clearWhatSelected)
                    }
                },
            )
            .show()
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_SHOWN)
    }

    private fun launchAutomaticallyClearWhenDialog(option: ClearWhenOption) {
        val currentOption = option.getOptionIndex()
        val clearWhenOptions = mutableListOf(
            R.string.settingsAutomaticallyClearWhenAppExitOnly,
            R.string.settingsAutomaticallyClearWhenAppExit5Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit15Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit30Minutes,
            R.string.settingsAutomaticallyClearWhenAppExit60Minutes,
        )
        if (appBuildConfig.isDebug) {
            clearWhenOptions.add(R.string.settingsAutomaticallyClearWhenAppExit5Seconds)
        }
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsAutomaticallyClearWhenDialogTitle)
            .setOptions(clearWhenOptions, currentOption)
            .setPositiveButton(R.string.settingsAutomaticallyClearingDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val clearWhenSelected = selectedItem.getClearWhenForIndex()
                        viewModel.onAutomaticallyWhenOptionSelected(clearWhenSelected)
                        Timber.d("Option selected: $clearWhenSelected")
                    }
                },
            )
            .show()
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.LaunchDefaultBrowser -> launchDefaultAppScreen()
            is Command.LaunchFeedback -> launchFeedback()
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchAutofillSettings -> launchAutofillSettings()
            is Command.LaunchAccessibilitySettings -> launchAccessibilitySettings()
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchWhitelist -> launchWhitelist()
            is Command.LaunchAppIcon -> launchAppIconChange()
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.LaunchAppTPTrackersScreen -> launchAppTPTrackersScreen()
            is Command.LaunchAppTPOnboarding -> launchAppTPOnboardingScreen()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
            is Command.LaunchEmailProtection -> launchEmailProtectionScreen(it.url)
            is Command.LaunchEmailProtectionNotSUpported -> launchEmailProtectionNotSupported()
            is Command.LaunchThemeSettings -> launchThemeSelector(it.theme)
            is Command.LaunchAppLinkSettings -> launchAppLinksSettingSelector(it.appLinksSettingType)
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector(it.animation)
            is Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option)
            is Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            is Command.LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget()
            is Command.LaunchMacOs -> launchMacOsScreen()
            is Command.LaunchAutoconsent -> launchAutoconsent()
            is Command.LaunchNotificationsSettings -> launchNotificationsSettings()
            is Command.LaunchWindows -> launchWindowsScreen()
            null -> TODO()
        }
    }

    private fun updateDefaultBrowserViewVisibility(it: SettingsViewModel.ViewState) {
        with(viewsGeneral.setAsDefaultBrowserSetting) {
            if (it.showDefaultBrowserSetting) {
                quietlySetIsChecked(it.isAppDefaultBrowser, defaultBrowserChangeListener)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun updateDeviceShieldSettings(
        appTPEnabled: Boolean,
        appTrackingProtectionOnboardingShown: Boolean,
    ) {
        with(viewsMore) {
            if (appTPEnabled) {
                vpnSetting.setSecondaryText(getString(R.string.atp_SettingsDeviceShieldEnabled))
            } else {
                if (appTrackingProtectionOnboardingShown) {
                    vpnSetting.setSecondaryText(getString(R.string.atp_SettingsDeviceShieldDisabled))
                } else {
                    vpnSetting.setSecondaryText(getString(R.string.atp_SettingsDeviceShieldNeverEnabled))
                }
            }
        }
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun launchDefaultAppScreen() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
            launchDefaultAppActivity()
        } else {
            throw IllegalStateException("Unable to launch default app activity on this OS")
        }
    }

    private fun launchFeedback() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivityForResult(Intent(FeedbackActivity.intent(this)), FEEDBACK_REQUEST_CODE, options)
    }

    private fun launchFireproofWebsites() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(FireproofWebsitesActivity.intent(this), options)
    }

    private fun launchAutofillSettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(autofillSettingsActivityLauncher.intent(this), options)
    }

    private fun launchAccessibilitySettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(AccessibilityActivity.intent(this), options)
    }

    private fun launchLocation() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(SitePermissionsActivity.intent(this), options)
    }

    private fun launchWhitelist() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(WhitelistActivity.intent(this), options)
    }

    private fun launchAppIconChange() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivityForResult(Intent(ChangeIconActivity.intent(this)), CHANGE_APP_ICON_REQUEST_CODE, options)
    }

    private fun launchFireAnimationSelector(animation: FireAnimation) {
        val currentAnimationOption = animation.getOptionIndex()

        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsSelectFireAnimationDialog)
            .setOptions(
                listOf(
                    R.string.settingsHeroFireAnimation,
                    R.string.settingsHeroWaterAnimation,
                    R.string.settingsHeroAbstractAnimation,
                    R.string.settingsNoneAnimation,
                ),
                currentAnimationOption,
            )
            .setPositiveButton(R.string.settingsSelectFireAnimationDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()

                        viewModel.onFireAnimationSelected(selectedAnimation)
                    }

                    override fun onRadioItemSelected(selectedItem: Int) {
                        val selectedAnimation = selectedItem.getAnimationForIndex()
                        if (selectedAnimation != None) {
                            startActivity(FireAnimationActivity.intent(baseContext, selectedAnimation))
                        }
                    }
                },
            )
            .show()
    }

    private fun launchThemeSelector(theme: DuckDuckGoTheme) {
        val currentTheme = theme.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsTheme)
            .setOptions(
                listOf(
                    R.string.settingsSystemTheme,
                    R.string.settingsLightTheme,
                    R.string.settingsDarkTheme,
                ),
                currentTheme,
            )
            .setPositiveButton(R.string.settingsThemeDialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedTheme = when (selectedItem) {
                            2 -> LIGHT
                            3 -> DARK
                            else -> SYSTEM_DEFAULT
                        }
                        viewModel.onThemeSelected(selectedTheme)
                    }
                },
            )
            .show()
    }

    private fun launchAppLinksSettingSelector(appLinkSettingType: AppLinkSettingType) {
        val currentAppLinkSetting = appLinkSettingType.getOptionIndex()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.settingsTitleAppLinksDialog)
            .setOptions(
                listOf(
                    R.string.settingsAppLinksAskEveryTime,
                    R.string.settingsAppLinksAlways,
                    R.string.settingsAppLinksNever,
                ),
                currentAppLinkSetting,
            )
            .setPositiveButton(R.string.dialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selectedAppLinkSetting = selectedItem.getAppLinkSettingForIndex()
                        viewModel.onAppLinksSettingChanged(selectedAppLinkSetting)
                    }
                },
            )
            .show()
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
    }

    private fun launchEmailProtectionScreen(url: String) {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(BrowserActivity.intent(this, url), options)
        this.finish()
    }

    private fun launchEmailProtectionNotSupported() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionUnsupportedActivity.intent(this), options)
    }

    private fun launchMacOsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(MacOsActivity.intent(this), options)
    }

    private fun launchWindowsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(WindowsWaitlistActivity.intent(this), options)
    }

    private fun launchAutoconsent() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(AutoconsentSettingsActivity.intent(this), options)
    }

    @SuppressLint("InlinedApi")
    private fun launchNotificationsSettings() {
        val settingsIntent = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(ANDROID_M_APP_NOTIFICATION_SETTINGS)
                .putExtra(ANDROID_M_APP_PACKAGE, packageName)
                .putExtra(ANDROID_M_APP_UID, applicationInfo.uid)
        }

        startActivity(settingsIntent, null)
    }

    private fun launchAppTPTrackersScreen() {
        startActivity(DeviceShieldTrackerActivity.intent(this))
    }

    private fun launchAppTPOnboardingScreen() {
        startActivity(VpnOnboardingActivity.intent(this))
    }

    private fun launchAddHomeScreenWidget() {
        pixel.fire(AppPixelName.SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED)
        addWidgetLauncher.launchAddWidget(this)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        when (requestCode) {
            FEEDBACK_REQUEST_CODE -> handleFeedbackResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleFeedbackResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, R.string.thanksForTheFeedback, Toast.LENGTH_LONG).show()
        }
    }

    @StringRes
    private fun ClearWhatOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> R.string.settingsAutomaticallyClearWhatOptionNone
            ClearWhatOption.CLEAR_TABS_ONLY -> R.string.settingsAutomaticallyClearWhatOptionTabs
            ClearWhatOption.CLEAR_TABS_AND_DATA -> R.string.settingsAutomaticallyClearWhatOptionTabsAndData
        }
    }

    @StringRes
    private fun ClearWhenOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> R.string.settingsAutomaticallyClearWhenAppExitOnly
            ClearWhenOption.APP_EXIT_OR_5_MINS -> R.string.settingsAutomaticallyClearWhenAppExit5Minutes
            ClearWhenOption.APP_EXIT_OR_15_MINS -> R.string.settingsAutomaticallyClearWhenAppExit15Minutes
            ClearWhenOption.APP_EXIT_OR_30_MINS -> R.string.settingsAutomaticallyClearWhenAppExit30Minutes
            ClearWhenOption.APP_EXIT_OR_60_MINS -> R.string.settingsAutomaticallyClearWhenAppExit60Minutes
            ClearWhenOption.APP_EXIT_OR_5_SECONDS -> R.string.settingsAutomaticallyClearWhenAppExit5Seconds
        }
    }

    companion object {
        private const val FEEDBACK_REQUEST_CODE = 100
        private const val CHANGE_APP_ICON_REQUEST_CODE = 101
        private const val PRIVACY_POLICY_WEB_LINK = "https://duckduckgo.com/privacy"

        private const val ANDROID_M_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        private const val ANDROID_M_APP_PACKAGE = "app_package"
        private const val ANDROID_M_APP_UID = "app_uid"

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
