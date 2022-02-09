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

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.accessibility.AccessibilityActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivitySettingsBinding
import com.duckduckgo.app.browser.webview.WebViewActivity
import com.duckduckgo.app.email.ui.EmailProtectionActivity
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.icon.ui.ChangeIconActivity
import com.duckduckgo.app.location.ui.LocationPermissionsActivity
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.settings.SettingsViewModel.AutomaticallyClearData
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.waitlist.trackerprotection.ui.AppTPWaitlistActivity
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistActivity
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.sendThemeChangedBroadcast
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState
import kotlinx.android.synthetic.main.content_settings_more.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class SettingsActivity :
    DuckDuckGoActivity(),
    SettingsAutomaticallyClearWhatFragment.Listener,
    SettingsAutomaticallyClearWhenFragment.Listener,
    SettingsThemeSelectorFragment.Listener,
    SettingsAppLinksSelectorFragment.Listener,
    SettingsFireAnimationSelectorFragment.Listener {

    private val viewModel: SettingsViewModel by bindViewModel()
    private val binding: ActivitySettingsBinding by viewBinding()

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var internalFeaturePlugins: PluginPoint<InternalFeaturePlugin>

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    private val defaultBrowserChangeListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onDefaultBrowserToggled(isChecked)
    }

    private val autocompleteToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    private val viewsGeneral
        get() = binding.includeSettings.contentSettingsGeneral

    private val viewsPrivacy
        get() = binding.includeSettings.contentSettingsPrivacy

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
        viewModel.start()
        viewModel.startPollingAppTpEnableState()
    }

    private fun configureUiEventHandlers() {
        with(viewsGeneral) {
            selectedThemeSetting.setOnClickListener { viewModel.userRequestedToChangeTheme() }
            autocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
            setAsDefaultBrowserSetting.setOnCheckedChangeListener(defaultBrowserChangeListener)
            changeAppIconLabel.setOnClickListener { viewModel.userRequestedToChangeIcon() }
            homeScreenWidgetSetting.setOnClickListener { viewModel.userRequestedToAddHomeScreenWidget() }
            selectedFireAnimationSetting.setOnClickListener { viewModel.userRequestedToChangeFireAnimation() }
            accessibilitySetting.setOnClickListener { viewModel.onAccessibilitySettingClicked() }
        }

        with(viewsPrivacy) {
            globalPrivacyControlSetting.setOnClickListener { viewModel.onGlobalPrivacyControlClicked() }
            fireproofWebsites.setOnClickListener { viewModel.onFireproofWebsitesClicked() }
            locationPermissions.setOnClickListener { viewModel.onLocationClicked() }
            automaticallyClearWhatSetting.setOnClickListener { viewModel.onAutomaticallyClearWhatClicked() }
            automaticallyClearWhenSetting.setOnClickListener { viewModel.onAutomaticallyClearWhenClicked() }
            whitelist.setOnClickListener { viewModel.onManageWhitelistSelected() }
            appLinksSetting.setOnClickListener { viewModel.userRequestedToChangeAppLinkSetting() }
        }

        with(viewsMore) {
            emailSetting.setOnClickListener { viewModel.onEmailProtectionSettingClicked() }
            deviceShieldSetting.setOnClickListener { viewModel.onAppTPSettingClicked() }
            macOsSetting.setOnClickListener { viewModel.onMacOsSettingClicked() }
        }

        with(viewsOther) {
            provideFeedback.setOnClickListener { viewModel.userRequestedToSendFeedback() }
            about.setOnClickListener { startActivity(AboutDuckDuckGoActivity.intent(this@SettingsActivity)) }
            privacyPolicy.setOnClickListener {
                startActivity(
                    WebViewActivity.intent(
                        this@SettingsActivity,
                        PRIVACY_POLICY_WEB_LINK,
                        getString(R.string.settingsPrivacyPolicyDuckduckgo)
                    )
                )
            }
        }
    }

    private fun configureInternalFeatures() {
        viewsInternal.settingsSectionInternal.visibility = if (internalFeaturePlugins.getPlugins().isEmpty()) View.GONE else View.VISIBLE
        internalFeaturePlugins.getPlugins().forEach { feature ->
            Timber.v("Adding internal feature ${feature.internalFeatureTitle()}")
            val view = SettingsOptionWithSubtitle(this).apply {
                setTitle(feature.internalFeatureTitle())
                this.setSubtitle(feature.internalFeatureSubtitle())
            }
            viewsInternal.settingsInternalFeaturesContainer.addView(view)
            view.setOnClickListener { feature.onInternalFeatureClicked(this) }
        }
    }

    private fun configureAppLinksSettingVisibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            viewsPrivacy.appLinksSetting.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    viewsOther.version.setSubtitle(it.version)
                    updateSelectedTheme(it.theme)
                    viewsGeneral.autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                    updateDefaultBrowserViewVisibility(it)
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    setGlobalPrivacyControlSetting(it.globalPrivacyControlEnabled)
                    viewsGeneral.changeAppIcon.setImageResource(it.appIcon.icon)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                    updateAppLinkBehavior(it.appLinksSettingType)
                    updateDeviceShieldSettings(it.appTrackingProtectionEnabled, it.appTrackingProtectionWaitlistState)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun setGlobalPrivacyControlSetting(enabled: Boolean) {
        val stateText = if (enabled) {
            getString(R.string.enabled)
        } else {
            getString(R.string.disabled)
        }
        viewsPrivacy.globalPrivacyControlSetting.setSubtitle(stateText)
    }

    private fun updateSelectedFireAnimation(fireAnimation: FireAnimation) {
        val subtitle = getString(fireAnimation.nameResId)
        viewsGeneral.selectedFireAnimationSetting.setSubtitle(subtitle)
    }

    private fun updateSelectedTheme(selectedTheme: DuckDuckGoTheme) {
        val subtitle = getString(
            when (selectedTheme) {
                DuckDuckGoTheme.DARK -> R.string.settingsDarkTheme
                DuckDuckGoTheme.LIGHT -> R.string.settingsLightTheme
                DuckDuckGoTheme.SYSTEM_DEFAULT -> R.string.settingsSystemTheme
            }
        )
        viewsGeneral.selectedThemeSetting.setSubtitle(subtitle)
    }

    private fun updateAppLinkBehavior(appLinkSettingType: AppLinkSettingType) {
        val subtitle = getString(
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> R.string.settingsAppLinksAskEveryTime
                AppLinkSettingType.ALWAYS -> R.string.settingsAppLinksAlways
                AppLinkSettingType.NEVER -> R.string.settingsAppLinksNever
            }
        )
        viewsPrivacy.appLinksSetting.setSubtitle(subtitle)
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhatSetting.setSubtitle(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhenSetting.setSubtitle(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        viewsPrivacy.automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    private fun launchAutomaticallyClearWhatDialog(option: ClearWhatOption) {
        val dialog = SettingsAutomaticallyClearWhatFragment.create(option)
        dialog.show(supportFragmentManager, CLEAR_WHAT_DIALOG_TAG)
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_SHOWN)
    }

    private fun launchAutomaticallyClearWhenDialog(option: ClearWhenOption) {
        val dialog = SettingsAutomaticallyClearWhenFragment.create(option)
        dialog.show(supportFragmentManager, CLEAR_WHEN_DIALOG_TAG)
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.LaunchDefaultBrowser -> launchDefaultAppScreen()
            is Command.LaunchFeedback -> launchFeedback()
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchAccessibilitySettigns -> launchAccessibilitySettings()
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchWhitelist -> launchWhitelist()
            is Command.LaunchAppIcon -> launchAppIconChange()
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.LaunchAppTPTrackersScreen -> launchAppTPTrackersScreen()
            is Command.LaunchAppTPOnboarding -> launchAppTPOnboardingScreen()
            is Command.LaunchAppTPWaitlist -> launchAppTPWaitlist()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
            is Command.LaunchEmailProtection -> launchEmailProtectionScreen()
            is Command.LaunchThemeSettings -> launchThemeSelector(it.theme)
            is Command.LaunchAppLinkSettings -> launchAppLinksSettingSelector(it.appLinksSettingType)
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector(it.animation)
            is Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option)
            is Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            is Command.LaunchAddHomeScreenWidget -> launchAddHomeScreenWidget()
            is Command.LaunchMacOs -> launchMacOsScreen()
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
        waitlistState: WaitlistState
    ) {
        with(viewsPrivacy) {
            if (waitlistState != WaitlistState.InBeta) {
                deviceShieldSetting.setSubtitle(getString(R.string.atp_SettingsDeviceShieldNeverEnabled))
            } else {
                if (appTPEnabled) {
                    deviceShieldSetting.setSubtitle(getString(R.string.atp_SettingsDeviceShieldEnabled))
                } else {
                    deviceShieldSetting.setSubtitle(getString(R.string.atp_SettingsDeviceShieldDisabled))
                }
            }
        }
    }

    private fun launchDefaultAppScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    private fun launchAccessibilitySettings() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(AccessibilityActivity.intent(this), options)
    }

    private fun launchLocation() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(LocationPermissionsActivity.intent(this), options)
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
        val dialog = SettingsFireAnimationSelectorFragment.create(animation)
        dialog.show(supportFragmentManager, FIRE_ANIMATION_SELECTOR_TAG)
    }

    private fun launchThemeSelector(theme: DuckDuckGoTheme) {
        val dialog = SettingsThemeSelectorFragment.create(theme)
        dialog.show(supportFragmentManager, THEME_SELECTOR_TAG)
    }

    private fun launchAppLinksSettingSelector(appLinkSettingType: AppLinkSettingType) {
        val dialog = SettingsAppLinksSelectorFragment.create(appLinkSettingType)
        dialog.show(supportFragmentManager, THEME_SELECTOR_TAG)
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
    }

    private fun launchEmailProtectionScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionActivity.intent(this), options)
    }

    private fun launchMacOsScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(MacOsWaitlistActivity.intent(this), options)
    }
    private fun launchAppTPTrackersScreen() {
        startActivity(DeviceShieldTrackerActivity.intent(this))
    }

    private fun launchAppTPOnboardingScreen() {
        startActivity(DeviceShieldOnboardingActivity.intent(this))
    }

    private val appTPWaitlistActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            startActivity(DeviceShieldOnboardingActivity.intent(this))
        }
    }

    private fun launchAppTPWaitlist() {
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
        appTPWaitlistActivityResult.launch(AppTPWaitlistActivity.intent(this), options)
    }

    private fun launchAddHomeScreenWidget() {
        pixel.fire(AppPixelName.SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED)
        addWidgetLauncher.launchAddWidget(this)
    }

    override fun onThemeSelected(selectedTheme: DuckDuckGoTheme) {
        viewModel.onThemeSelected(selectedTheme)
    }

    override fun onAppLinkSettingSelected(selectedSetting: AppLinkSettingType) {
        viewModel.onAppLinksSettingChanged(selectedSetting)
    }

    override fun onAutomaticallyClearWhatOptionSelected(clearWhatSetting: ClearWhatOption) {
        viewModel.onAutomaticallyWhatOptionSelected(clearWhatSetting)
    }

    override fun onAutomaticallyClearWhenOptionSelected(clearWhenSetting: ClearWhenOption) {
        viewModel.onAutomaticallyWhenOptionSelected(clearWhenSetting)
    }

    override fun onFireAnimationSelected(selectedFireAnimation: FireAnimation) {
        viewModel.onFireAnimationSelected(selectedFireAnimation)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
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
        private const val FIRE_ANIMATION_SELECTOR_TAG = "FIRE_ANIMATION_SELECTOR_DIALOG_FRAGMENT"
        private const val THEME_SELECTOR_TAG = "THEME_SELECTOR_DIALOG_FRAGMENT"
        private const val CLEAR_WHAT_DIALOG_TAG = "CLEAR_WHAT_DIALOG_FRAGMENT"
        private const val CLEAR_WHEN_DIALOG_TAG = "CLEAR_WHEN_DIALOG_FRAGMENT"
        private const val FEEDBACK_REQUEST_CODE = 100
        private const val CHANGE_APP_ICON_REQUEST_CODE = 101
        private const val PRIVACY_POLICY_WEB_LINK = "https://duckduckgo.com/privacy"

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
