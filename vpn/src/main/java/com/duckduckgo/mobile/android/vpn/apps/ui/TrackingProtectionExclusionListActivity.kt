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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.Command
import com.duckduckgo.mobile.android.vpn.apps.ExcludedAppsViewModel
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.databinding.ActivityTrackingProtectionExclusionListBinding
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class TrackingProtectionExclusionListActivity :
    DuckDuckGoActivity(),
    ManuallyEnableAppProtectionDialog.ManuallyEnableAppsProtectionDialogListener,
    ManuallyDisableAppProtectionDialog.ManuallyDisableAppProtectionDialogListener,
    RestoreDefaultProtectionDialog.RestoreDefaultProtectionDialogListener {

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: ActivityTrackingProtectionExclusionListBinding by viewBinding()

    private val viewModel: ExcludedAppsViewModel by bindViewModel()

    lateinit var adapter: TrackingProtectionAppsAdapter

    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.deviceShieldExclusionAppListSkeleton) }

    private val reportBreakage = registerForActivityResult(ReportBreakageContract()) { result ->
        if (!result.isEmpty()) {
            Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.defaultToolbar)
        setupRecycler()

        bindViews()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_exclusion_list_activity, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val restoreDefault = menu?.findItem(R.id.restoreDefaults)
        restoreDefault?.isEnabled = viewModel.userMadeChanges()

        return super.onPrepareOptionsMenu(menu)
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
        binding.excludedAppsDisabledVPNLabel.apply {
            setClickableLink(
                REPORT_ISSUES_ANNOTATION,
                getText(R.string.atp_ActivityDisabledLabel)
            ) { launchFeedback() }
        }
        setupRecycler()
    }

    private fun setupRecycler() {
        adapter = TrackingProtectionAppsAdapter(object : AppProtectionListener {
            override fun onAppProtectionChanged(
                excludedAppInfo: TrackingProtectionAppInfo,
                enabled: Boolean,
                position: Int
            ) {
                viewModel.onAppProtectionChanged(excludedAppInfo, position, enabled)
            }
        })

        val recyclerView = binding.excludedAppsRecycler
        val isListEnabled = intent.getBooleanExtra(KEY_LIST_ENABLED, false)

        if (isListEnabled) {
            binding.excludedAppsEnabledVPNLabel.show()
            binding.excludedAppsDisabledVPNLabel.gone()
        } else {
            binding.excludedAppsEnabledVPNLabel.gone()
            binding.excludedAppsDisabledVPNLabel.show()
            recyclerView.alpha = 0.45f
        }

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
        val isListEnabled = intent.getBooleanExtra(KEY_LIST_ENABLED, false)
        adapter.update(viewState.excludedApps, isListEnabled)
        shimmerLayout.gone()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
            is Command.ShowDisableProtectionDialog -> showDisableProtectionDialog(
                command.excludingReason
            )
            is Command.ShowEnableProtectionDialog -> showEnableProtectionDialog(
                command.excludingReason,
                command.position
            )
            is Command.LaunchFeedback -> reportBreakage.launch(command.reportBreakageScreen)
        }
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch {
            TrackerBlockingVpnService.restartVpnService(applicationContext)
        }
    }

    private fun showDisableProtectionDialog(excludedAppInfo: TrackingProtectionAppInfo) {
        val dialog = ManuallyDisableAppProtectionDialog.instance(excludedAppInfo)
        dialog.show(
            supportFragmentManager,
            ManuallyDisableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_DISABLE
        )
    }

    private fun showEnableProtectionDialog(
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int
    ) {
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

    override fun onAppProtectionEnabled(
        packageName: String,
        excludingReason: Int
    ) {
        viewModel.onAppProtectionEnabled(packageName, excludingReason, needsPixel = true)
    }

    override fun onDialogSkipped(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun onAppProtectionDisabled(
        answer: Int,
        appName: String,
        packageName: String,
        skippedReport: Boolean
    ) {
        viewModel.onAppProtectionDisabled(answer, appName = appName, packageName = packageName, skippedReport = skippedReport)
    }

    private fun launchFeedback() {
        viewModel.launchFeedback()
    }

    companion object {
        private const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        private const val KEY_LIST_ENABLED = "KEY_LIST_ENABLED"
        fun intent(
            context: Context,
            isRunning: Boolean
        ): Intent {
            val intent = Intent(context, TrackingProtectionExclusionListActivity::class.java)
            intent.putExtra(KEY_LIST_ENABLED, isRunning)
            return intent
        }
    }

    override fun onDefaultProtectionRestored() {
        viewModel.restoreProtectedApps()
        restartVpn()
        Snackbar.make(shimmerLayout, getString(R.string.atp_ExcludeAppsRestoreDefaultSnackbar), Snackbar.LENGTH_LONG)
            .show()
    }
}
