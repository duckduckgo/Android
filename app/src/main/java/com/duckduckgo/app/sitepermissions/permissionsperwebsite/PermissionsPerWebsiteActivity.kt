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

package com.duckduckgo.app.sitepermissions.permissionsperwebsite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityPermissionPerWebsiteBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.GoBackToSitePermissions
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class PermissionsPerWebsiteActivity : DuckDuckGoActivity(), PermissionsSettingsSelectorFragment.Listener {

    private val viewModel: PermissionsPerWebsiteViewModel by bindViewModel()
    private val binding: ActivityPermissionPerWebsiteBinding by viewBinding()
    private val adapter: PermissionSettingAdapter by lazy { PermissionSettingAdapter(viewModel) }
    private val url: String by lazy { intent.getStringExtra(EXTRA_URL) ?: "" }

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setViews()
        observeViewModel()
        viewModel.websitePermissionSettings(url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
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
        supportActionBar?.title = url.websiteFromGeoLocationsApiOrigin()
        binding.sitePermissionsSectionTitle.text = String.format(
            getString(R.string.permissionPerWebsiteText),
            url.websiteFromGeoLocationsApiOrigin()
        )
        binding.permissionsPerWebsiteRecyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.viewState
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { state ->
                    updatePermissionsList(state.websitePermissions)
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
        adapter.updateItems(permissionsSettings)
    }

    private fun showPermissionSettingSelectionDialog(setting: WebsitePermissionSetting) {
        val dialog = PermissionsSettingsSelectorFragment.create(setting, url.websiteFromGeoLocationsApiOrigin())
        dialog.show(supportFragmentManager, PERMISSIONS_SETTING_SELECTOR_DIALOG_TAG)
    }

    companion object {
        private const val EXTRA_URL = "URL"
        private const val PERMISSIONS_SETTING_SELECTOR_DIALOG_TAG = "PERMISSIONS_SETTING_SELECTOR_DIALOG_TAG"

        fun intent(context: Context, url: String): Intent {
            val intent = Intent(context, PermissionsPerWebsiteActivity::class.java)
            intent.putExtra(EXTRA_URL, url)
            return intent
        }
    }

    override fun onPermissionSettingSelected(websitePermissionSetting: WebsitePermissionSetting) {
        viewModel.onPermissionSettingSelected(websitePermissionSetting, url)
    }
}
