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
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPermissionsBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
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
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionSetting
import com.duckduckgo.site.permissions.impl.ui.SitePermissionScreenNoParams
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsAdapter
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteActivity
import com.duckduckgo.site.permissions.impl.ui.showGlobalPermissionDialog
import com.google.android.material.snackbar.Snackbar
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

    @Inject
    lateinit var faviconManager: FaviconManager

    private val viewModel: PermissionsViewModel by bindViewModel()
    private val sitePermissionsViewModel: SitePermissionsViewModel by bindViewModel()
    private val binding: ActivityPermissionsBinding by viewBinding()
    private var appLinksDialog: DaxAlertDialog? = null
    private var pendingAppLinkSetting: AppLinkSettingType? = null
    private var sitePermissionDialog: DaxAlertDialog? = null
    private var pendingSitePermissionSetting: SitePermissionSetting? = null
    private var generalPermissionsAdapter: GeneralPermissionsAdapter? = null
    private var sitePermissionsAdapter: SitePermissionsAdapter? = null

    private val permissionSettingsRedesign by lazy { sitePermissionsDialogRedesignFeature.permissionSettingsRedesign().isEnabled() }

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

        if (permissionSettingsRedesign) {
            setupRedesignedList()
            observeSitePermissionsViewModel()
        } else {
            configureUiEventHandlers()
        }
        observeViewModel()
        savedInstanceState?.getString(KEY_PENDING_APP_LINK_SETTING)
            ?.let { launchRedesignedAppLinksSettingSelector(AppLinkSettingType.valueOf(it)) }
        savedInstanceState?.takeIf { it.containsKey(KEY_PENDING_SITE_PERMISSION) }?.let {
            showSitePermissionDialog(SitePermissionSetting(it.getInt(KEY_PENDING_SITE_PERMISSION), it.getBoolean(KEY_PENDING_SITE_ASK_ENABLED)))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingAppLinkSetting?.let { outState.putString(KEY_PENDING_APP_LINK_SETTING, it.name) }
        pendingSitePermissionSetting?.let {
            outState.putInt(KEY_PENDING_SITE_PERMISSION, it.text)
            outState.putBoolean(KEY_PENDING_SITE_ASK_ENABLED, it.askEnabled)
        }
    }

    override fun onDestroy() {
        appLinksDialog?.dismiss()
        sitePermissionDialog?.dismiss()
        super.onDestroy()
    }

    private fun setupRedesignedList() {
        binding.includePermissions.root.isVisible = false
        binding.redesignRecycler.isVisible = true
        val general = GeneralPermissionsAdapter(
            onNotificationsClicked = { viewModel.userRequestedToChangeNotificationsSetting() },
            onAppLinksClicked = { viewModel.userRequestedToChangeAppLinkSetting() },
        )
        val sitePermissions = SitePermissionsAdapter(
            viewModel = sitePermissionsViewModel,
            lifecycleOwner = this,
            faviconManager = faviconManager,
            appBrandDesignUpdateToggles = appBrandDesignUpdateToggles,
            permissionSettingsRedesign = true,
            onPermissionSettingClicked = { showSitePermissionDialog(it) },
        )
        // Hold the saved scroll position until the sites have loaded, otherwise a position inside Manage Sites is dropped.
        sitePermissions.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        generalPermissionsAdapter = general
        sitePermissionsAdapter = sitePermissions
        binding.redesignRecycler.adapter = ConcatAdapter(general, sitePermissions)
    }

    private fun observeSitePermissionsViewModel() {
        sitePermissionsViewModel.allowedSites()
        sitePermissionsViewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                sitePermissionsAdapter?.updateItems(
                    state.sitesPermissionsAllowed.map { it.domain },
                    state.askLocationEnabled,
                    state.askCameraEnabled,
                    state.askMicEnabled,
                    state.askDrmEnabled,
                )
                if (state.sitesLoaded) {
                    sitePermissionsAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW
                }
            }.launchIn(lifecycleScope)

        sitePermissionsViewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is SitePermissionsViewModel.Command.ShowRemovedAllConfirmationSnackbar -> showRemovedAllSitesSnackbar {
                        sitePermissionsViewModel.onSnackBarUndoRemoveAllWebsites(command.removedSitePermissions)
                    }
                    is SitePermissionsViewModel.Command.LaunchWebsiteAllowed ->
                        startActivity(PermissionsPerWebsiteActivity.intent(this, command.domain))
                }
            }.launchIn(lifecycleScope)
    }

    private fun showSitePermissionDialog(setting: SitePermissionSetting) {
        pendingSitePermissionSetting = setting
        sitePermissionDialog = showGlobalPermissionDialog(
            context = this,
            permission = setting.text,
            askEnabled = setting.askEnabled,
            onSelectionChanged = { pendingSitePermissionSetting = setting.copy(askEnabled = it) },
            onDismissed = {
                pendingSitePermissionSetting = null
                sitePermissionDialog = null
            },
            onSave = { sitePermissionsViewModel.permissionToggleSelected(it, setting.text) },
        )
    }

    private fun showRemovedAllSitesSnackbar(onUndo: () -> Unit) {
        val message = HtmlCompat.fromHtml(
            getString(com.duckduckgo.site.permissions.impl.R.string.sitePermissionsRemoveAllWebsitesSnackbarText),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(com.duckduckgo.mobile.android.R.string.undo) { onUndo() }
            .show()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.includePermissions.root, drawBehindGestureNav = true)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.redesignRecycler)
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
                val appLinksSubtitle = getAppLinksSubtitle(viewState.appLinksSettingType)
                val notificationsSubtitle = getString(viewState.notificationsSettingSubtitleId)
                generalPermissionsAdapter?.update(notificationsSubtitle, appLinksSubtitle) ?: run {
                    binding.includePermissions.appLinksSetting.setSecondaryText(appLinksSubtitle)
                    binding.includePermissions.notificationsSetting.setSecondaryText(notificationsSubtitle)
                }
            }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun getAppLinksSubtitle(appLinkSettingType: AppLinkSettingType): String = getString(
        if (permissionSettingsRedesign) {
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> com.duckduckgo.site.permissions.impl.R.string.permissionSettingsAskEachTime
                AppLinkSettingType.ALWAYS -> com.duckduckgo.site.permissions.impl.R.string.permissionSettingsAlwaysAllow
                AppLinkSettingType.NEVER -> com.duckduckgo.site.permissions.impl.R.string.sitePermissionsDialogNeverAllowButton
            }
        } else {
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> R.string.settingsAppLinksAskEveryTime
                AppLinkSettingType.ALWAYS -> R.string.settingsAppLinksAlways
                AppLinkSettingType.NEVER -> R.string.settingsAppLinksNever
            }
        },
    )

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
        if (permissionSettingsRedesign) {
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
        private const val KEY_PENDING_SITE_PERMISSION = "pendingSitePermission"
        private const val KEY_PENDING_SITE_ASK_ENABLED = "pendingSiteAskEnabled"
    }
}
