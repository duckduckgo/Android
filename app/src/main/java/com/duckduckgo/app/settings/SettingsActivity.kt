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
import androidx.annotation.StringRes
import androidx.lifecycle.Observer
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.beta.BetaFeaturesActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.sendThemeChangedBroadcast
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.global.view.quietlySetIsChecked
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.icon.ui.ChangeIconActivity
import com.duckduckgo.app.location.ui.LocationPermissionsActivity
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.settings.SettingsViewModel.AutomaticallyClearData
import com.duckduckgo.app.settings.SettingsViewModel.EmailSetting
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName
import kotlinx.android.synthetic.main.content_settings_general.*
import kotlinx.android.synthetic.main.content_settings_other.*
import kotlinx.android.synthetic.main.content_settings_privacy.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class SettingsActivity :
    DuckDuckGoActivity(),
    SettingsAutomaticallyClearWhatFragment.Listener,
    SettingsAutomaticallyClearWhenFragment.Listener,
    SettingsFireAnimationSelectorFragment.Listener {

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: SettingsViewModel by bindViewModel()

    private val defaultBrowserChangeListener = OnCheckedChangeListener { _, _ -> launchDefaultAppScreen() }

    private val lightThemeToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onLightThemeToggled(isChecked)
    }

    private val autocompleteToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun configureUiEventHandlers() {
        changeAppIconLabel.setOnClickListener { viewModel.userRequestedToChangeIcon() }
        selectedFireAnimationSetting.setOnClickListener { viewModel.userRequestedToChangeFireAnimation() }
        about.setOnClickListener { startActivity(AboutDuckDuckGoActivity.intent(this)) }
        provideFeedback.setOnClickListener { viewModel.userRequestedToSendFeedback() }
        fireproofWebsites.setOnClickListener { viewModel.onFireproofWebsitesClicked() }
        locationPermissions.setOnClickListener { viewModel.onLocationClicked() }
        globalPrivacyControlSetting.setOnClickListener { viewModel.onGlobalPrivacyControlClicked() }

        lightThemeToggle.setOnCheckedChangeListener(lightThemeToggleListener)
        autocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
        setAsDefaultBrowserSetting.setOnCheckedChangeListener(defaultBrowserChangeListener)
        automaticallyClearWhatSetting.setOnClickListener { launchAutomaticallyClearWhatDialog() }
        automaticallyClearWhenSetting.setOnClickListener { launchAutomaticallyClearWhenDialog() }
        whitelist.setOnClickListener { viewModel.onManageWhitelistSelected() }
        emailSetting.setOnClickListener { viewModel.onEmailSettingClicked() }
        betaFeaturesSetting.setOnClickListener { viewModel.onBetaFeatureSettingsClicked() }
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    version.setSubtitle(it.version)
                    lightThemeToggle.quietlySetIsChecked(it.lightThemeEnabled, lightThemeToggleListener)
                    autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                    updateDefaultBrowserViewVisibility(it)
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    setGlobalPrivacyControlSetting(it.globalPrivacyControlEnabled)
                    changeAppIcon.setImageResource(it.appIcon.icon)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                    setEmailSetting(it.emailSetting)
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer {
                processCommand(it)
            }
        )

        lifecycle.addObserver(viewModel)
    }

    private fun setEmailSetting(emailData: EmailSetting) {
        when (emailData) {
            is EmailSetting.EmailSettingOff -> {
                emailSetting.gone()
            }
            is EmailSetting.EmailSettingOn -> {
                emailSetting.show()
                emailSetting.setSubtitle(getString(R.string.settingsEmailAutofillEnabledFor, emailData.emailAddress))
            }
        }
    }

    private fun setGlobalPrivacyControlSetting(enabled: Boolean) {
        val stateText = if (enabled) {
            getString(R.string.enabled)
        } else {
            getString(R.string.disabled)
        }
        globalPrivacyControlSetting.setSubtitle(stateText)
    }

    private fun updateSelectedFireAnimation(fireAnimation: FireAnimation) {
        val subtitle = getString(fireAnimation.nameResId)
        selectedFireAnimationSetting.setSubtitle(subtitle)
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        automaticallyClearWhatSetting.setSubtitle(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        automaticallyClearWhenSetting.setSubtitle(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    private fun launchEmailDialog() {
        val dialog = SettingsEmailLogoutDialog.create()
        dialog.show(supportFragmentManager, EMAIL_DIALOG_TAG)
        dialog.onLogout = { viewModel.onEmailLogout() }
    }

    private fun launchAutomaticallyClearWhatDialog() {
        val dialog = SettingsAutomaticallyClearWhatFragment.create(viewModel.viewState.value?.automaticallyClearData?.clearWhatOption)
        dialog.show(supportFragmentManager, CLEAR_WHAT_DIALOG_TAG)
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_SHOWN)
    }

    private fun launchAutomaticallyClearWhenDialog() {
        val dialog = SettingsAutomaticallyClearWhenFragment.create(viewModel.viewState.value?.automaticallyClearData?.clearWhenOption)
        dialog.show(supportFragmentManager, CLEAR_WHEN_DIALOG_TAG)
        pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.LaunchFeedback -> launchFeedback()
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchWhitelist -> launchWhitelist()
            is Command.LaunchAppIcon -> launchAppIconChange()
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.LaunchBetaFeatures -> launchBetaFeaturesScreen()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector()
            is Command.LaunchEmailDialog -> launchEmailDialog()
        }
    }

    private fun updateDefaultBrowserViewVisibility(it: SettingsViewModel.ViewState) {
        if (it.showDefaultBrowserSetting) {
            setAsDefaultBrowserSetting.quietlySetIsChecked(it.isAppDefaultBrowser, defaultBrowserChangeListener)
            setAsDefaultBrowserSetting.visibility = View.VISIBLE
        } else {
            setAsDefaultBrowserSetting.visibility = View.GONE
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

    private fun launchFireAnimationSelector() {
        val dialog = SettingsFireAnimationSelectorFragment.create(viewModel.viewState.value?.selectedFireAnimation)
        dialog.show(supportFragmentManager, FIRE_ANIMATION_SELECTOR_TAG)
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
    }

    private fun launchBetaFeaturesScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(BetaFeaturesActivity.intent(this), options)
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            FEEDBACK_REQUEST_CODE -> handleFeedbackResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
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
        private const val CLEAR_WHAT_DIALOG_TAG = "CLEAR_WHAT_DIALOG_FRAGMENT"
        private const val CLEAR_WHEN_DIALOG_TAG = "CLEAR_WHEN_DIALOG_FRAGMENT"
        private const val EMAIL_DIALOG_TAG = "EMAIL_DIALOG_FRAGMENT"
        private const val FEEDBACK_REQUEST_CODE = 100
        private const val CHANGE_APP_ICON_REQUEST_CODE = 101
        private const val REQUEST_DEVICE_SHIELD_ONBOARDING = 102

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
