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

package com.duckduckgo.app.permissions

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPermissionsBinding
import com.duckduckgo.app.permissions.PermissionsViewModel.Command
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.getAppLinkSettingForIndex
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.RadioListOption
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.impl.ui.SitePermissionScreenNoParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PermissionsScreenNoParams::class)
class PermissionsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var sitePermissionsDialogRedesignFeature: SitePermissionsDialogRedesignFeature

    private val viewModel: PermissionsViewModel by bindViewModel()
    private val binding: ActivityPermissionsBinding by viewBinding()
    private var appLinksDialog: DaxAlertDialog? = null
    private var pendingAppLinkSetting: AppLinkSettingType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureUiEventHandlers()
        observeViewModel()
        savedInstanceState?.getString(KEY_PENDING_APP_LINK_SETTING)
            ?.let { launchRedesignedAppLinksSettingSelector(AppLinkSettingType.valueOf(it)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingAppLinkSetting?.let { outState.putString(KEY_PENDING_APP_LINK_SETTING, it.name) }
    }

    override fun onDestroy() {
        appLinksDialog?.dismiss()
        super.onDestroy()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.includePermissions.root, drawBehindGestureNav = true)
    }

    override fun onStart() {
        super.onStart()

        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        viewModel.start(notificationsEnabled)
    }

    private fun configureUiEventHandlers() {
        binding.includePermissions.sitePermissions.setClickListener { viewModel.onSitePermissionsClicked() }
        binding.includePermissions.notificationsSetting.setClickListener { viewModel.userRequestedToChangeNotificationsSetting() }
        binding.includePermissions.appLinksSetting.setClickListener { viewModel.userRequestedToChangeAppLinkSetting() }
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { viewState ->
                viewState.let {
                    updateAppLinkBehavior(it.appLinksSettingType)
                    binding.includePermissions.notificationsSetting.setSecondaryText(getString(it.notificationsSettingSubtitleId))
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
        binding.includePermissions.appLinksSetting.setSecondaryText(subtitle)
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
        globalActivityStarter.start(this, SitePermissionScreenNoParams, options)
    }

    private fun launchAppLinksSettingSelector(appLinkSettingType: AppLinkSettingType) {
        if (sitePermissionsDialogRedesignFeature.permissionSettingsRedesign().isEnabled()) {
            launchRedesignedAppLinksSettingSelector(appLinkSettingType)
            return
        }
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
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
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

    private fun launchRedesignedAppLinksSettingSelector(appLinkSettingType: AppLinkSettingType) {
        val options = listOf(
            AppLinkSettingType.ALWAYS to com.duckduckgo.site.permissions.impl.R.string.permissionSettingsAlwaysAllow,
            AppLinkSettingType.ASK_EVERYTIME to com.duckduckgo.site.permissions.impl.R.string.permissionSettingsAskEachTime,
            AppLinkSettingType.NEVER to com.duckduckgo.site.permissions.impl.R.string.sitePermissionsDialogNeverAllowButton,
        )
        pendingAppLinkSetting = appLinkSettingType
        appLinksDialog = RadioListAlertDialogBuilder(this)
            .setRebrandUpdate(true)
            .setCancelable(true)
            .setTitle(R.string.settingsTitleAppLinksDialog)
            .setOptions(options.map { (setting, text) -> RadioListOption(text, isSelected = setting == appLinkSettingType) })
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onRadioItemSelected(selectedItem: Int) {
                        pendingAppLinkSetting = options[selectedItem - 1].first
                    }

                    override fun onDialogDismissed() {
                        pendingAppLinkSetting = null
                        appLinksDialog = null
                    }

                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        viewModel.onAppLinksSettingChanged(options[selectedItem - 1].first)
                    }
                },
            )
            .build()
            .also { it.show() }
    }

    @SuppressLint("InlinedApi")
    private fun launchNotificationsSettings() {
        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

        startActivity(settingsIntent, null)
    }

    companion object {
        private const val KEY_PENDING_APP_LINK_SETTING = "pendingAppLinkSetting"
    }
}
