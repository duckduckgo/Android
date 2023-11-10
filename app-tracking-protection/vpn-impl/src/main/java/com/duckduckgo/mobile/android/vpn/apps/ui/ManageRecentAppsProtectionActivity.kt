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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.isIgnoringBatteryOptimizations
import com.duckduckgo.app.global.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.app.global.extensions.launchIgnoreBatteryOptimizationSettings
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.apps.Command
import com.duckduckgo.mobile.android.vpn.apps.ManageAppsProtectionViewModel
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityManageRecentAppsProtectionBinding
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@InjectWith(ActivityScope::class)
class ManageRecentAppsProtectionActivity :
    DuckDuckGoActivity(),
    ManuallyEnableAppProtectionDialog.ManuallyEnableAppsProtectionDialogListener,
    ManuallyDisableAppProtectionDialog.ManuallyDisableAppProtectionDialogListener,
    RestoreDefaultProtectionDialog.RestoreDefaultProtectionDialogListener {

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var reportBreakageContract: Provider<ReportBreakageContract>

    @Inject lateinit var appBuildConfig: AppBuildConfig

    private val binding: ActivityManageRecentAppsProtectionBinding by viewBinding()

    private val viewModel: ManageAppsProtectionViewModel by bindViewModel()

    lateinit var adapter: TrackingProtectionAppsAdapter

    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.manageRecentAppsSkeleton) }

    private val isAppTPEnabled by lazy {
        runBlocking {
            vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)
        }
    }

    private val unrestrictedBatteryUsageListener = CompoundButton.OnCheckedChangeListener { _, _ ->
        this.launchIgnoreBatteryOptimizationSettings()
    }

    private lateinit var reportBreakage: ActivityResultLauncher<ReportBreakageScreen>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportBreakage = registerForActivityResult(reportBreakageContract.get()) { result ->
            if (!result.isEmpty()) {
                Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, LENGTH_LONG).show()
            }
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupRecycler()

        bindViews()
        observeViewModel()

        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    @SuppressLint("InlinedApi") // lint doesn't detect appBuildConfig
    private fun bindViews() {
        binding.manageRecentAppsSkeleton.startShimmer()
        binding.alwaysOn.setOnClickListener {
            this.launchAlwaysOnSystemSettings(appBuildConfig.sdkInt)
        }
        binding.unrestrictedBatteryUsage.quietlySetIsChecked(this.isIgnoringBatteryOptimizations(), unrestrictedBatteryUsageListener)
        binding.manageRecentAppsReportIssues.addClickableLink(
            REPORT_ISSUES_ANNOTATION,
            getText(R.string.atp_ManageRecentAppsProtectionReportIssues),
        ) {
            launchFeedback()
        }
        binding.manageRecentAppsShowAll.setOnClickListener {
            launchManageAppsProtection()
        }
        setupRecycler()
    }

    override fun onResume() {
        super.onResume()
        binding.unrestrictedBatteryUsage.quietlySetIsChecked(this.isIgnoringBatteryOptimizations(), unrestrictedBatteryUsageListener)
    }

    private fun setupRecycler() {
        adapter = TrackingProtectionAppsAdapter(
            object : AppProtectionListener {
                override fun onAppProtectionChanged(
                    excludedAppInfo: TrackingProtectionAppInfo,
                    enabled: Boolean,
                    position: Int,
                ) {
                    viewModel.onAppProtectionChanged(excludedAppInfo, position, enabled)
                }
            },
        )

        val recyclerView = binding.manageRecentAppsRecycler

        if (isAppTPEnabled) {
            recyclerView.alpha = 1.0f
        } else {
            recyclerView.alpha = 0.45f
        }

        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getRecentApps()
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
        if (viewState.excludedApps.isEmpty()) {
            binding.manageRecentAppsRecycler.gone()
            binding.manageRecentAppsEmptyView.show()
        } else {
            binding.manageRecentAppsEmptyView.gone()
            adapter.update(viewState.excludedApps, isAppTPEnabled)
            binding.manageRecentAppsRecycler.show()
        }
        binding.manageRecentAppsShowAll.show()
        binding.manageRecentAppsDivider.show()
        shimmerLayout.gone()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
            is Command.ShowDisableProtectionDialog -> showDisableProtectionDialog(
                command.excludingReason,
            )

            is Command.ShowEnableProtectionDialog -> showEnableProtectionDialog(
                command.excludingReason,
                command.position,
            )

            is Command.LaunchFeedback -> reportBreakage.launch(command.reportBreakageScreen)
            is Command.LaunchAllAppsProtection -> startActivity(TrackingProtectionExclusionListActivity.intent(this))
        }
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch {
            vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    private fun showDisableProtectionDialog(excludedAppInfo: TrackingProtectionAppInfo) {
        val dialog = ManuallyDisableAppProtectionDialog.instance(excludedAppInfo)
        dialog.show(
            supportFragmentManager,
            ManuallyDisableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_DISABLE,
        )
    }

    private fun showEnableProtectionDialog(
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int,
    ) {
        val dialog = ManuallyEnableAppProtectionDialog.instance(excludedAppInfo, position)
        dialog.show(
            supportFragmentManager,
            ManuallyEnableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_ENABLE,
        )
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onAppProtectionEnabled(packageName: String) {
        viewModel.onAppProtectionEnabled(packageName)
    }

    override fun onDialogSkipped(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean,
    ) {
        viewModel.onAppProtectionDisabled(appName = appName, packageName = packageName, report = report)
    }

    private fun launchFeedback() {
        viewModel.launchFeedback()
    }

    private fun launchManageAppsProtection() {
        viewModel.launchManageAppsProtection()
    }

    override fun onDefaultProtectionRestored() {
        viewModel.restoreProtectedApps()
        Snackbar.make(shimmerLayout, getString(R.string.atp_ExcludeAppsRestoreDefaultSnackbar), Snackbar.LENGTH_LONG)
            .show()
    }

    companion object {
        private const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        internal fun intent(
            context: Context,
        ): Intent {
            return Intent(context, ManageRecentAppsProtectionActivity::class.java)
        }
    }
}
