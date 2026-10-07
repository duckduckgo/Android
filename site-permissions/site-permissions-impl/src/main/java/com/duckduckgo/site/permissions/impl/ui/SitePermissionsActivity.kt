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

package com.duckduckgo.site.permissions.impl.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.text.HtmlCompat
import androidx.core.text.htmlEncode
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.databinding.ActivitySitePermissionsBinding
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionSetting
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.LaunchWebsiteAllowed
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.ShowRemovedAllConfirmationSnackbar
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.ShowRemovedSiteConfirmationSnackbar
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SitePermissionScreenNoParams::class)
class SitePermissionsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var sitePermissionsDialogRedesignFeature: SitePermissionsDialogRedesignFeature

    private val viewModel: SitePermissionsViewModel by bindViewModel()
    private val binding: ActivitySitePermissionsBinding by viewBinding()
    private lateinit var adapter: SitePermissionsAdapter
    private var permissionDialog: DaxAlertDialog? = null
    private var pendingPermissionSetting: SitePermissionSetting? = null
    private val websitePermissionsLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        PermissionsPerWebsiteActivity.removedUrl(result.data)?.let { viewModel.removeSiteSelected(it) }
    }

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(toolbar)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }
        setupRecyclerView()
        observeViewModel()
        viewModel.allowedSites()
        savedInstanceState?.takeIf { it.containsKey(KEY_PENDING_PERMISSION) }?.let {
            showPermissionDialog(SitePermissionSetting(it.getInt(KEY_PENDING_PERMISSION), it.getBoolean(KEY_PENDING_ASK_ENABLED)))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPermissionSetting?.let {
            outState.putInt(KEY_PENDING_PERMISSION, it.text)
            outState.putBoolean(KEY_PENDING_ASK_ENABLED, it.askEnabled)
        }
    }

    override fun onDestroy() {
        permissionDialog?.dismiss()
        super.onDestroy()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.recycler, drawBehindGestureNav = true)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.viewState
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { state ->
                    val sitePermissionsWebsites = state.sitesPermissionsAllowed.map { it.domain }
                    updateList(sitePermissionsWebsites, state.askLocationEnabled, state.askCameraEnabled, state.askMicEnabled, state.askDrmEnabled)
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
            is ShowRemovedAllConfirmationSnackbar -> showRemovedSitesSnackbar(getString(R.string.sitePermissionsRemoveAllWebsitesSnackbarText)) {
                viewModel.onSnackBarUndoRemoveAllWebsites(command.removedSitePermissions)
            }
            is LaunchWebsiteAllowed -> launchWebsiteAllowed(command.domain)
            is ShowRemovedSiteConfirmationSnackbar -> showRemovedSitesSnackbar(
                getString(R.string.permissionsRemovedForSiteSnackbar, command.domain.htmlEncode()),
            ) { viewModel.onSnackBarUndoRemoveSite(command) }
        }
    }

    private fun showRemovedSitesSnackbar(
        html: String,
        onUndo: () -> Unit,
    ) {
        Snackbar.make(binding.root, HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY), Snackbar.LENGTH_LONG)
            .setAction(com.duckduckgo.mobile.android.R.string.undo) { onUndo() }
            .show()
    }

    private fun updateList(
        sitesAllowed: List<String>,
        askLocationEnabled: Boolean,
        askCameraEnabled: Boolean,
        askMicEnabled: Boolean,
        askDrmEnabled: Boolean,
    ) {
        adapter.updateItems(sitesAllowed, askLocationEnabled, askCameraEnabled, askMicEnabled, askDrmEnabled)
    }

    private fun setupRecyclerView() {
        adapter = SitePermissionsAdapter(
            viewModel = viewModel,
            lifecycleOwner = this,
            faviconManager = faviconManager,
            appBrandDesignUpdateToggles = appBrandDesignUpdateToggles,
            permissionSettingsRedesign = sitePermissionsDialogRedesignFeature.permissionSettingsRedesign().isEnabled(),
            onPermissionSettingClicked = { showPermissionDialog(it) },
        )
        binding.recycler.adapter = adapter
    }

    private fun showPermissionDialog(setting: SitePermissionSetting) {
        pendingPermissionSetting = setting
        permissionDialog = showGlobalPermissionDialog(
            context = this,
            permission = setting.text,
            askEnabled = setting.askEnabled,
            onSelectionChanged = { pendingPermissionSetting = setting.copy(askEnabled = it) },
            onDismissed = {
                pendingPermissionSetting = null
                permissionDialog = null
            },
            onSave = { viewModel.permissionToggleSelected(it, setting.text) },
        )
    }

    private fun launchWebsiteAllowed(domain: String) {
        websitePermissionsLauncher.launch(PermissionsPerWebsiteActivity.intent(this, domain))
    }

    companion object {
        private const val KEY_PENDING_PERMISSION = "pendingPermission"
        private const val KEY_PENDING_ASK_ENABLED = "pendingAskEnabled"
    }
}
