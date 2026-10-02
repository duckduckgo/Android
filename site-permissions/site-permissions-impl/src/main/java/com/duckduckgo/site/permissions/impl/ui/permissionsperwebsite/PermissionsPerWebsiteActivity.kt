/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.BundleCompat
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.RadioListOption
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.common.utils.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.databinding.ActivityPermissionPerWebsiteBinding
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.GoBackToSitePermissions
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ALLOW
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK_DISABLED
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.Companion.getPermissionSettingOptionFromPosition
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.DENY
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class PermissionsPerWebsiteActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var sitePermissionsDialogRedesignFeature: SitePermissionsDialogRedesignFeature

    @Inject
    lateinit var faviconManager: FaviconManager

    private val viewModel: PermissionsPerWebsiteViewModel by bindViewModel()
    private val binding: ActivityPermissionPerWebsiteBinding by viewBinding()
    private val permissionSettingsRedesign by lazy { sitePermissionsDialogRedesignFeature.permissionSettingsRedesign().isEnabled() }
    private val permissionSettingsAdapter: PermissionSettingAdapter by lazy { PermissionSettingAdapter(viewModel, permissionSettingsRedesign) }
    private val url: String by lazy { intent.getStringExtra(EXTRA_URL) ?: "" }
    private var permissionSettingDialog: DaxAlertDialog? = null
    private var pendingPermissionSetting: WebsitePermissionSetting? = null
    private var restoredPermissionSetting: WebsitePermissionSetting? = null

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setViews()
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }
        observeViewModel()
        viewModel.websitePermissionSettings(url)
        restoredPermissionSetting = savedInstanceState?.let {
            BundleCompat.getSerializable(it, KEY_PENDING_PERMISSION_SETTING, WebsitePermissionSetting::class.java)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        (pendingPermissionSetting ?: restoredPermissionSetting)?.let { outState.putSerializable(KEY_PENDING_PERMISSION_SETTING, it) }
    }

    override fun onDestroy() {
        permissionSettingDialog?.dismiss()
        super.onDestroy()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.permissionsPerWebsiteRecyclerView, drawBehindGestureNav = true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (permissionSettingsRedesign) return false
        menuInflater.inflate(R.menu.menu_permissions_per_website_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.remove -> {
                viewModel.removeWebsitePermissionsSettings(url)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setViews() {
        setupToolbar(toolbar)
        val website = url.websiteFromGeoLocationsApiOrigin()
        supportActionBar?.title = website
        if (permissionSettingsRedesign) {
            binding.sitePermissionsSectionHeader.primaryText = getString(R.string.permissionsPerWebsiteRedesignHeader, website)
            binding.permissionsPerWebsiteRecyclerView.adapter = ConcatAdapter(
                permissionSettingsAdapter,
                RemovePermissionsAdapter {
                    // The caller deletes the site so it can offer an undo once this screen is gone
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_REMOVED_URL, url))
                    finish()
                },
            )
            loadToolbarFavicon(website)
        } else {
            binding.sitePermissionsSectionHeader.primaryText = String.format(getString(R.string.permissionPerWebsiteText), website)
            binding.permissionsPerWebsiteRecyclerView.adapter = permissionSettingsAdapter
        }
    }

    private fun loadToolbarFavicon(website: String) {
        val size = resources.getDimensionPixelSize(CommonR.dimen.toolbarIconSize)
        supportActionBar?.apply {
            setDisplayUseLogoEnabled(true)
            setLogo(getToolbarLogo(faviconManager.generateDefaultFavicon(placeholder = null, domain = website).toBitmap(size, size)))
        }
        lifecycleScope.launch {
            val favicon = faviconManager.loadFromDiskWithParams(
                url = url,
                cornerRadius = resources.getDimensionPixelSize(CommonR.dimen.keyline_0),
                width = size,
                height = size,
            ) ?: return@launch
            supportActionBar?.setLogo(getToolbarLogo(favicon))
        }
    }

    private fun getToolbarLogo(favicon: Bitmap): Drawable {
        val gap = resources.getDimensionPixelSize(CommonR.dimen.keyline_2)
        return InsetDrawable(favicon.toDrawable(resources), 0, 0, gap, 0)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.viewState
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { state ->
                    updatePermissionsList(state.websitePermissions)
                    // Saving reads the loaded list, so a dialog restored after process death must wait for it
                    if (state.websitePermissions.isNotEmpty()) {
                        restoredPermissionSetting?.let { showPermissionSettingSelectionDialog(it) }
                        restoredPermissionSetting = null
                    }
                }
        }
        lifecycleScope.launch {
            viewModel.commands
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { processCommand(it) }
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is ShowPermissionSettingSelectionDialog -> showPermissionSettingSelectionDialog(command.setting)
            is GoBackToSitePermissions -> finish()
        }
    }

    private fun updatePermissionsList(permissionsSettings: List<WebsitePermissionSetting>) {
        permissionSettingsAdapter.updateItems(permissionsSettings)
    }

    private fun showPermissionSettingSelectionDialog(currentOption: WebsitePermissionSetting) {
        if (permissionSettingsRedesign) {
            showRedesignedPermissionSettingSelectionDialog(currentOption)
            return
        }
        val dialogTitle = String.format(
            getString(R.string.permissionsPerWebsiteSelectorDialogTitle),
            getString(currentOption.title),
            url.websiteFromGeoLocationsApiOrigin(),
        )
        RadioListAlertDialogBuilder(this)
            .setTitle(dialogTitle)
            .setOptions(
                listOf(
                    ASK.stringRes,
                    DENY.stringRes,
                    ALLOW.stringRes,
                ),
                currentOption.setting.order,
            )
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(com.duckduckgo.mobile.android.R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val permissionSettingSelected = selectedItem.getPermissionSettingOptionFromPosition()
                        val newPermissionSetting = WebsitePermissionSetting(currentOption.icon, currentOption.title, permissionSettingSelected)
                        logcat { "Permissions: permissionSettingSelected $permissionSettingSelected" }
                        logcat { "Permissions: newPermissionSetting $newPermissionSetting" }
                        viewModel.onPermissionSettingSelected(newPermissionSetting, url)
                    }
                },
            )
            .show()
    }

    private fun showRedesignedPermissionSettingSelectionDialog(currentOption: WebsitePermissionSetting) {
        val options = listOf(
            ALLOW to R.string.permissionSettingsAlwaysAllow,
            ASK to R.string.permissionSettingsAskEachTime,
            DENY to R.string.sitePermissionsDialogNeverAllowButton,
        )
        val current = if (currentOption.setting == ASK_DISABLED) ASK else currentOption.setting
        pendingPermissionSetting = currentOption
        permissionSettingDialog = RadioListAlertDialogBuilder(this)
            .setRebrandUpdate(true)
            .setCancelable(true)
            .setHeaderImageResource(currentOption.icon)
            .setTitle(
                getString(
                    R.string.permissionsPerWebsiteSelectorDialogTitle,
                    getString(currentOption.title),
                    url.websiteFromGeoLocationsApiOrigin(),
                ),
            )
            .setOptions(options.map { (setting, text) -> RadioListOption(text, isSelected = setting == current) })
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(com.duckduckgo.mobile.android.R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onRadioItemSelected(selectedItem: Int) {
                        pendingPermissionSetting = currentOption.copy(setting = options[selectedItem - 1].first)
                    }

                    override fun onDialogDismissed() {
                        pendingPermissionSetting = null
                        permissionSettingDialog = null
                    }

                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        val selected = options[selectedItem - 1].first
                        viewModel.onPermissionSettingSelected(currentOption.copy(setting = selected), url)
                    }
                },
            )
            .build()
            .also { it.show() }
    }

    companion object {
        private const val EXTRA_URL = "URL"
        private const val EXTRA_REMOVED_URL = "REMOVED_URL"
        private const val KEY_PENDING_PERMISSION_SETTING = "pendingPermissionSetting"

        fun removedUrl(result: Intent?): String? = result?.getStringExtra(EXTRA_REMOVED_URL)

        fun intent(
            context: Context,
            url: String,
        ): Intent {
            val intent = Intent(context, PermissionsPerWebsiteActivity::class.java)
            intent.putExtra(EXTRA_URL, url)
            return intent
        }
    }
}
