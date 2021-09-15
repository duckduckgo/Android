/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.Command
import com.duckduckgo.mobile.android.vpn.apps.ExcludedAppsViewModel
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.apps.VpnExcludedInstalledAppInfo
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.Snackbar
import dummy.ui.VpnControllerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TrackingProtectionExclusionListActivity :
    DuckDuckGoActivity(),
    ManuallyEnableAppProtectionDialog.ManuallyEnableAppsProtectionDialogListener,
    ManuallyDisableAppProtectionDialog.ManuallyDisableAppProtectionDialogListener,
    RestoreDefaultProtectionDialog.RestoreDefaultProtectionDialogListener {

    private val viewModel: ExcludedAppsViewModel by bindViewModel()

    lateinit var adapter: TrackingProtectionAppsAdapter

    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.deviceShieldExclusionAppListSkeleton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tracking_protection_exclusion_list)
        setupToolbar(findViewById(R.id.default_toolbar))
        setupRecycler()

        bindViews()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_exclusion_list_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.restoreDefaults -> {
                val dialog = RestoreDefaultProtectionDialog.instance()
                dialog.show(supportFragmentManager, RestoreDefaultProtectionDialog.TAG_RESTORE_DEFAULT_PROTECTION)
                true
            }
            R.id.reportIssue -> {
                launchFeedback(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bindViews() {
        shimmerLayout.startShimmer()
        setupRecycler()
    }

    private fun setupRecycler() {
        adapter = TrackingProtectionAppsAdapter(object : AppProtectionListener {
            override fun onAppProtectionChanged(
                excludedAppInfo: VpnExcludedInstalledAppInfo,
                enabled: Boolean,
                position: Int
            ) {
                viewModel.onAppProtectionChanged(excludedAppInfo, position, enabled)
            }
        })

        val recyclerView = findViewById<RecyclerView>(R.id.excludedAppsRecycler)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getProtectedApps()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        shimmerLayout.stopShimmer()
        adapter.update(viewState.excludedApps)
        shimmerLayout.gone()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
            is Command.ShowDisableProtectionDialog -> showDisableProtectionDialog(command.excludingReason)
            is Command.ShowEnableProtectionDialog -> showEnableProtectionDialog(command.excludingReason, command.position)
            is Command.LaunchFeedback -> startActivity(Intent(Intent.ACTION_VIEW, VpnControllerActivity.FEEDBACK_URL))
        }
    }

    private fun restartVpn() {
        lifecycleScope.launch {
            TrackerBlockingVpnService.restartVpnService(applicationContext)
        }
    }

    private fun showDisableProtectionDialog(excludedAppInfo: VpnExcludedInstalledAppInfo) {
        val dialog = ManuallyDisableAppProtectionDialog.instance(excludedAppInfo)
        dialog.show(
            supportFragmentManager,
            ManuallyDisableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_DISABLE
        )
    }

    private fun showEnableProtectionDialog(excludedAppInfo: VpnExcludedInstalledAppInfo, position: Int) {
        val dialog = ManuallyEnableAppProtectionDialog.instance(excludedAppInfo, position)
        dialog.show(
            supportFragmentManager,
            ManuallyEnableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_ENABLE
        )
    }

    override fun onPause() {
        viewModel.onLeavingScreen()
        super.onPause()
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onAppProtectionEnabled(packageName: String, excludingReason: Int) {
        viewModel.onAppProtectionEnabled(packageName, excludingReason)
    }

    override fun onDialogSkipped(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun onAppProtectionDisabled(answer: Int, packageName: String) {
        viewModel.onAppProtectionDisabled(answer, packageName)
    }

    private fun launchFeedback() {
        viewModel.launchFeedback()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, TrackingProtectionExclusionListActivity::class.java)
        }
    }

    override fun onDefaultProtectionRestored() {
        viewModel.restoreProtectedApps()
        lifecycleScope.launch(Dispatchers.IO) {
            TrackerBlockingVpnService.restartVpnService(applicationContext)
        }
        Snackbar.make(shimmerLayout, getString(R.string.atp_ExcludeAppsRestoreDefaultSnackbar), Snackbar.LENGTH_LONG).show()
    }

}
