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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.ui.EmailProtectionActivity
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.icon.ui.ChangeIconActivity
import com.duckduckgo.app.location.ui.LocationPermissionsActivity
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.settings.SettingsViewModel.AutomaticallyClearData
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.extension.InternalFeaturePlugin
import com.duckduckgo.mobile.android.ui.sendThemeChangedBroadcast
import kotlinx.android.synthetic.main.content_settings_general.*
import kotlinx.android.synthetic.main.content_settings_internal.*
import kotlinx.android.synthetic.main.content_settings_other.*
import kotlinx.android.synthetic.main.content_settings_privacy.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class SettingsActivity :
    DuckDuckGoActivity(),
    SettingsAutomaticallyClearWhatFragment.Listener,
    SettingsAutomaticallyClearWhenFragment.Listener,
    SettingsFireAnimationSelectorFragment.Listener {

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var internalFeaturePlugins: PluginPoint<InternalFeaturePlugin>

    private val viewModel: SettingsViewModel by bindViewModel()

    private val defaultBrowserChangeListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onDefaultBrowserChanged(isChecked)
    }

    private val lightThemeToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onLightThemeToggled(isChecked)
    }

    private val autocompleteToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    private val appLinksToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppLinksSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        configureInternalFeatures()
        configureAppLinksToggle()
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
        automaticallyClearWhatSetting.setOnClickListener { viewModel.onAutomaticallyClearWhatClicked() }
        automaticallyClearWhenSetting.setOnClickListener { viewModel.onAutomaticallyClearWhenClicked() }
        whitelist.setOnClickListener { viewModel.onManageWhitelistSelected() }
        emailSetting.setOnClickListener { viewModel.onEmailProtectionSettingClicked() }
    }

    private fun configureInternalFeatures() {
        settingsSectionInternal.visibility = if (internalFeaturePlugins.getPlugins().isEmpty()) View.GONE else View.VISIBLE
        internalFeaturePlugins.getPlugins().forEach { feature ->
            Timber.v("Adding internal feature ${feature.internalFeatureTitle()}")
            val view = SettingsOptionWithSubtitle(this).apply {
                setTitle(feature.internalFeatureTitle())
                this.setSubtitle(feature.internalFeatureSubtitle())
            }
            settingsInternalFeaturesContainer.addView(view)
            view.setOnClickListener { feature.onInternalFeatureClicked(this) }
        }
    }

    private fun configureAppLinksToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appLinksToggle.setOnCheckedChangeListener(appLinksToggleListener)
        } else {
            appLinksToggle.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    version.setSubtitle(it.version)
                    lightThemeToggle.quietlySetIsChecked(it.lightThemeEnabled, lightThemeToggleListener)
                    autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                    updateDefaultBrowserViewVisibility(it)
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    setGlobalPrivacyControlSetting(it.globalPrivacyControlEnabled)
                    changeAppIcon.setImageResource(it.appIcon.icon)
                    updateSelectedFireAnimation(it.selectedFireAnimation)
                    appLinksToggle.quietlySetIsChecked(it.appLinksEnabled, appLinksToggleListener)
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
            is Command.LaunchDefaultAppScreen -> launchDefaultAppScreen()
            is Command.LaunchFeedback -> launchFeedback()
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchWhitelist -> launchWhitelist()
            is Command.LaunchAppIcon -> launchAppIconChange()
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
            is Command.LaunchEmailProtection -> launchEmailProtectionScreen()
            is Command.LaunchFireAnimationSettings -> launchFireAnimationSelector(it.animation)
            is Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option)
            is Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            null -> TODO()
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

    private fun launchFireAnimationSelector(animation: FireAnimation) {
        val dialog = SettingsFireAnimationSelectorFragment.create(animation)
        dialog.show(supportFragmentManager, FIRE_ANIMATION_SELECTOR_TAG)
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
    }

    private fun launchEmailProtectionScreen() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionActivity.intent(this), options)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FEEDBACK_REQUEST_CODE) {
            handleFeedbackResult(resultCode)
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
        private const val CLEAR_WHAT_DIALOG_TAG = "CLEAR_WHAT_DIALOG_FRAGMENT"
        private const val CLEAR_WHEN_DIALOG_TAG = "CLEAR_WHEN_DIALOG_FRAGMENT"
        private const val FEEDBACK_REQUEST_CODE = 100
        private const val CHANGE_APP_ICON_REQUEST_CODE = 101

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
