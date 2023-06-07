/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.permissionsandprivacy

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPermissionsAndPrivacyBinding
import com.duckduckgo.app.fire.fireproofwebsite.ui.FireproofWebsitesActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlActivity
import com.duckduckgo.app.permissionsandprivacy.PermissionsAndPrivacyViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.ui.WhitelistActivity
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.getAppLinkSettingForIndex
import com.duckduckgo.app.settings.clear.getClearWhatOptionForIndex
import com.duckduckgo.app.settings.clear.getClearWhenForIndex
import com.duckduckgo.app.sitepermissions.SitePermissionsActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.impl.ui.AutoconsentSettingsActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class PermissionsAndPrivacyActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: PermissionsAndPrivacyViewModel by bindViewModel()
    private val binding: ActivityPermissionsAndPrivacyBinding by viewBinding()

    private val viewsPrivacy
        get() = binding.includePermissionsAndPrivacy.contentPermissionsAndPrivacySectionPrivacy

    private val viewsCustomize
        get() = binding.includePermissionsAndPrivacy.contentPermissionsAndPrivacySectionCustomize

    private val autocompleteToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
        configureAppLinksSettingVisibility()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()

        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        viewModel.start(notificationsEnabled)
    }

    private fun configureUiEventHandlers() {
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
                    viewsCustomize.autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                    updateAutomaticClearDataOptions(it.automaticallyClearData)
                    setGlobalPrivacyControlSetting(it.globalPrivacyControlEnabled)
                    setAutoconsentSetting(it.autoconsentEnabled)
                    updateAppLinkBehavior(it.appLinksSettingType)
                    viewsCustomize.notificationsSetting.setSecondaryText(getString(it.notificationsSettingSubtitleId))
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: PermissionsAndPrivacyViewModel.AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhatSetting.setSecondaryText(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        viewsPrivacy.automaticallyClearWhenSetting.setSecondaryText(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        viewsPrivacy.automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
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

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchFireproofWebsites -> launchFireproofWebsites()
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchWhitelist -> launchWhitelist()
            is Command.LaunchGlobalPrivacyControl -> launchGlobalPrivacyControl()
            is Command.LaunchAppLinkSettings -> launchAppLinksSettingSelector(it.appLinksSettingType)
            is Command.ShowClearWhatDialog -> launchAutomaticallyClearWhatDialog(it.option)
            is Command.ShowClearWhenDialog -> launchAutomaticallyClearWhenDialog(it.option)
            is Command.LaunchAutoconsent -> launchAutoconsent()
            is Command.LaunchNotificationsSettings -> launchNotificationsSettings()
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

    private fun launchFireproofWebsites() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(FireproofWebsitesActivity.intent(this), options)
    }

    private fun launchLocation() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(SitePermissionsActivity.intent(this), options)
    }

    private fun launchWhitelist() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(WhitelistActivity.intent(this), options)
    }

    private fun launchGlobalPrivacyControl() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(GlobalPrivacyControlActivity.intent(this), options)
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

    companion object {
        private const val ANDROID_M_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        private const val ANDROID_M_APP_PACKAGE = "app_package"
        private const val ANDROID_M_APP_UID = "app_uid"

        fun intent(context: Context): Intent {
            return Intent(context, PermissionsAndPrivacyActivity::class.java)
        }
    }
}
