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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPermissionsAndPrivacyBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.permissionsandprivacy.PermissionsAndPrivacyViewModel.Command
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.getAppLinkSettingForIndex
import com.duckduckgo.app.sitepermissions.SitePermissionsActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class PermissionsAndPrivacyActivity : DuckDuckGoActivity() {

    // TODO: [ANA] Rename this class to "PermissionsActivity" + package + everything else

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: PermissionsAndPrivacyViewModel by bindViewModel()
    private val binding: ActivityPermissionsAndPrivacyBinding by viewBinding()

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
        binding.includePermissionsAndPrivacy.sitePermissions.setClickListener { viewModel.onSitePermissionsClicked() }
        binding.includePermissionsAndPrivacy.notificationsSetting.setClickListener { viewModel.userRequestedToChangeNotificationsSetting() }
        binding.includePermissionsAndPrivacy.appLinksSetting.setClickListener { viewModel.userRequestedToChangeAppLinkSetting() }
    }

    private fun configureAppLinksSettingVisibility() {
        if (appBuildConfig.sdkInt < Build.VERSION_CODES.N) {
            binding.includePermissionsAndPrivacy.appLinksSetting.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateAppLinkBehavior(it.appLinksSettingType)
                    binding.includePermissionsAndPrivacy.notificationsSetting.setSecondaryText(getString(it.notificationsSettingSubtitleId))
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun updateAppLinkBehavior(appLinkSettingType: AppLinkSettingType) {
        val subtitle = getString(
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> R.string.settingsAppLinksAskEveryTime
                AppLinkSettingType.ALWAYS -> R.string.settingsAppLinksAlways
                AppLinkSettingType.NEVER -> R.string.settingsAppLinksNever
            },
        )
        binding.includePermissionsAndPrivacy.appLinksSetting.setSecondaryText(subtitle)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is Command.LaunchLocation -> launchLocation()
            is Command.LaunchAppLinkSettings -> launchAppLinksSettingSelector(it.appLinksSettingType)
            is Command.LaunchNotificationsSettings -> launchNotificationsSettings()
        }
    }

    private fun launchLocation() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(SitePermissionsActivity.intent(this), options)
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
