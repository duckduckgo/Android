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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.InfoPanel.Companion.APPTP_SETTINGS_ANNOTATION
import com.duckduckgo.mobile.android.ui.view.InfoPanel.Companion.REPORT_ISSUES_ANNOTATION
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.ManageRecentAppsProtectionActivity
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityDeviceShieldActivityBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldFAQActivity
import com.duckduckgo.mobile.android.vpn.ui.report.DeviceShieldAppTrackersInfo
import com.google.android.material.snackbar.Snackbar
import dummy.ui.VpnControllerActivity
import dummy.ui.VpnDiagnosticsActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class DeviceShieldTrackerActivity :
    DuckDuckGoActivity(),
    DeviceShieldActivityFeedFragment.DeviceShieldActivityFeedListener,
    AppTPDisableConfirmationDialog.Listener,
    AppTPVpnConflictDialog.Listener,
    AppTPRemoveFeatureConfirmationDialog.Listener {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val binding: ActivityDeviceShieldActivityBinding by viewBinding()

    private lateinit var deviceShieldSwitch: SwitchCompat

    // we might get an update before options menu has been populated; temporarily cache value to use when menu populated
    private var vpnCachedState: VpnState? = null

    private val feedConfig = DeviceShieldActivityFeedFragment.ActivityFeedConfig(
        maxRows = 6,
        timeWindow = 5,
        timeWindowUnits = TimeUnit.DAYS,
        showTimeWindowHeadings = false
    )

    private val viewModel: DeviceShieldTrackerActivityViewModel by bindViewModel()

    private val reportBreakage = registerForActivityResult(ReportBreakageContract()) {
        if (!it.isEmpty()) {
            Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
        }
    }

    private val enableAppTPSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppTPToggleSwitched(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.trackersToolbar)

        bindViews()
        showDeviceShieldActivity()
        observeViewModel()

        deviceShieldPixels.didShowSummaryTrackerActivity()
    }

    private fun bindViews() {
        binding.ctaTrackerFaq.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchDeviceShieldFAQ)
        }

        binding.ctaBetaInstructions.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchBetaInstructions)
        }

        binding.ctaWhatAreAppTrackers.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchAppTrackersFAQ)
        }

        binding.ctaManageProtection.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchExcludedApps)
        }

        binding.ctaRemoveFeature.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.RemoveFeature)
        }

        binding.ctaShowAll.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchMostRecentActivity)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onVPNPermissionResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onTrackerListShowed(totalTrackers: Int) {
        if (totalTrackers >= MIN_ROWS_FOR_ALL_ACTIVITY) {
            binding.ctaShowAll.show()
        } else {
            binding.ctaShowAll.gone()
        }
    }

    private fun showDeviceShieldActivity() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.activity_list,
                DeviceShieldActivityFeedFragment.newInstance(feedConfig)
            )
            .commitNow()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getBlockedTrackersCount()
                .combine(viewModel.getTrackingAppsCount()) { trackers, apps ->
                    DeviceShieldTrackerActivityViewModel.TrackerCountInfo(trackers, apps)
                }
                .combine(viewModel.getRunningState()) { trackerCountInfo, runningState ->
                    DeviceShieldTrackerActivityViewModel.TrackerActivityViewState(trackerCountInfo, runningState)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        intent.getParcelableExtra<ResultReceiver>(RESULT_RECEIVER_EXTRA)?.let {
            it.send(ON_LAUNCHED_CALLED_SUCCESS, null)
        }
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun processCommand(it: DeviceShieldTrackerActivityViewModel.Command?) {
        when (it) {
            is DeviceShieldTrackerActivityViewModel.Command.StopVPN -> stopDeviceShield()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchVPN -> startVPN()
            is DeviceShieldTrackerActivityViewModel.Command.CheckVPNPermission -> checkVPNPermission()
            is DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission -> obtainVpnRequestPermission(it.vpnIntent)
            is DeviceShieldTrackerActivityViewModel.Command.LaunchAppTrackersFAQ -> launchAppTrackersFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchBetaInstructions -> launchBetaInstructions()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchDeviceShieldFAQ -> launchDeviceShieldFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchManageAppsProtection -> launchManageAppsProtection()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchMostRecentActivity -> launchMostRecentActivity()
            is DeviceShieldTrackerActivityViewModel.Command.ShowDisableVpnConfirmationDialog -> launchDisableConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowVpnConflictDialog -> launchVPNConflictDialog(false)
            is DeviceShieldTrackerActivityViewModel.Command.ShowVpnAlwaysOnConflictDialog -> launchVPNConflictDialog(true)
            is DeviceShieldTrackerActivityViewModel.Command.VPNPermissionNotGranted -> quietlyToggleAppTpSwitch(false)
            is DeviceShieldTrackerActivityViewModel.Command.ShowDisableVpnConfirmationDialog -> launchDisableConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowRemoveFeatureConfirmationDialog -> launchRemoveFeatureConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.CloseScreen -> finish()
        }
    }

    private fun launchManageAppsProtection() {
        deviceShieldPixels.didOpenManageRecentAppSettings()
        startActivity(ManageRecentAppsProtectionActivity.intent(this))
    }

    private fun launchDeviceShieldFAQ() {
        startActivity(DeviceShieldFAQActivity.intent(this))
    }

    private fun launchDisableConfirmationDialog() {
        deviceShieldSwitch.quietlySetIsChecked(true, enableAppTPSwitchListener)
        deviceShieldPixels.didShowDisableTrackingProtectionDialog()
        val dialog = AppTPDisableConfirmationDialog.instance(this)
        dialog.show(
            supportFragmentManager,
            AppTPDisableConfirmationDialog.TAG_APPTP_DISABLE_DIALOG
        )
    }

    private fun launchRemoveFeatureConfirmationDialog() {
        deviceShieldPixels.didShowRemoveTrackingProtectionFeatureDialog()
        val dialog = AppTPRemoveFeatureConfirmationDialog.instance(this)
        dialog.show(
            supportFragmentManager,
            AppTPRemoveFeatureConfirmationDialog.TAG_APPTP_REMOVE_FEATURE_DIALOG
        )
    }

    private fun launchVPNConflictDialog(isAlwaysOn: Boolean) {
        quietlyToggleAppTpSwitch(false)
        deviceShieldPixels.didShowVpnConflictDialog()
        val dialog = AppTPVpnConflictDialog.instance(this, isAlwaysOn)
        dialog.show(
            supportFragmentManager,
            AppTPVpnConflictDialog.TAG_VPN_CONFLICT_DIALOG
        )
    }

    override fun onOpenAppProtection() {
        deviceShieldPixels.didChooseToDisableOneAppFromDialog()
        viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchExcludedApps)
    }

    override fun onTurnAppTrackingProtectionOff() {
        quietlyToggleAppTpSwitch(false)
        deviceShieldPixels.didChooseToDisableTrackingProtectionFromDialog()
        viewModel.onAppTpManuallyDisabled()
    }

    override fun onDisableDialogCancelled() {
        deviceShieldPixels.didChooseToCancelTrackingProtectionDialog()
    }

    override fun onDismissConflictDialog() {
        deviceShieldPixels.didChooseToDismissVpnConflictDialog()
    }

    override fun onOpenSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflictDialog()

        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onContinue() {
        deviceShieldPixels.didChooseToContinueFromVpnConflictDialog()
        checkVPNPermission()
    }

    override fun onCancel() {
        deviceShieldPixels.didChooseToCancelRemoveTrakcingProtectionDialog()
    }

    override fun onRemoveFeature() {
        viewModel.removeFeature()
    }

    private fun launchBetaInstructions() {
        val intent = Intent(this, Class.forName("com.duckduckgo.app.browser.webview.WebViewActivity"))
        intent.putExtra("URL_EXTRA", getString(R.string.atp_WaitlistBetaBlogPost))
        intent.putExtra("TITLE_EXTRA", getString(R.string.atp_ActivityBetaInstructions))
        startActivity(intent)
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                deviceShieldPixels.enableFromSummaryTrackerActivity()
                startVPN()
            }
            is VpnPermissionStatus.Denied -> {
                viewModel.onVPNPermissionNeeded(permissionStatus.intent)
            }
        }
    }

    private fun checkVpnPermissionStatus(): VpnPermissionStatus {
        val intent = VpnService.prepare(applicationContext)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun launchAppTrackersFAQ() {
        startActivity(DeviceShieldAppTrackersInfo.intent(this))
    }

    private fun launchMostRecentActivity() {
        deviceShieldPixels.didShowDetailedTrackerActivity()
        startActivity(DeviceShieldMostRecentActivity.intent(this))
    }

    private fun startVPN() {
        quietlyToggleAppTpSwitch(true)
        TrackerBlockingVpnService.startService(this)
    }

    private fun stopDeviceShield() {
        quietlyToggleAppTpSwitch(false)
        TrackerBlockingVpnService.stopService(this)
    }

    private fun quietlyToggleAppTpSwitch(state: Boolean) {
        deviceShieldSwitch.quietlySetIsChecked(state, enableAppTPSwitchListener)
    }

    private fun renderViewState(state: DeviceShieldTrackerActivityViewModel.TrackerActivityViewState) {
        vpnCachedState = state.runningState
        if (::deviceShieldSwitch.isInitialized) {
            quietlyToggleAppTpSwitch(state.runningState.state == VpnRunningState.ENABLED)
        } else {
            Timber.v("switch view reference not yet initialized; cache value until menu populated")
        }

        updateCounts(state.trackerCountInfo)
        updateRunningState(state.runningState)
    }

    private fun updateCounts(trackerCountInfo: DeviceShieldTrackerActivityViewModel.TrackerCountInfo) {
        binding.trackersBlockedCount.count = trackerCountInfo.stringTrackerCount()
        binding.trackersBlockedCount.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekTrackerCount, trackerCountInfo.trackers.value)

        binding.trackingAppsCount.count = trackerCountInfo.stringAppsCount()
        binding.trackingAppsCount.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekAppCount, trackerCountInfo.apps.value)
    }

    private fun updateRunningState(runningState: VpnState) {
        if (runningState.state == VpnRunningState.ENABLED) {
            Timber.d("updateRunningState enabled")
            binding.deviceShieldTrackerLabelDisabled.gone()

            binding.deviceShieldTrackerLabelEnabled.apply {
                setClickableLink(
                    APPTP_SETTINGS_ANNOTATION,
                    getText(R.string.atp_ActivityEnabledLabel)
                ) { launchManageAppsProtection() }
                show()
            }
        } else {
            binding.deviceShieldTrackerLabelEnabled.gone()

            val disabledLabel = if (runningState.stopReason == REVOKED) {
                Timber.d("updateRunningState revoked")
                R.string.atp_ActivityRevokedLabel
            } else {
                Timber.d("updateRunningState disabled")
                R.string.atp_ActivityDisabledLabel
            }
            binding.deviceShieldTrackerLabelDisabled.apply {
                setClickableLink(
                    REPORT_ISSUES_ANNOTATION,
                    getText(disabledLabel)
                ) { launchFeedback() }
                show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_tracker_activity, menu)

        val switchMenuItem = menu.findItem(R.id.deviceShieldSwitch)
        deviceShieldSwitch = switchMenuItem?.actionView as SwitchCompat
        deviceShieldSwitch.setOnCheckedChangeListener(enableAppTPSwitchListener)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.customDnsServer)?.let {
            it.isChecked = viewModel.isCustomDnsServerSet()
            it.isEnabled = !TrackerBlockingVpnService.isServiceRunning(this)
        }
        menu.findItem(R.id.diagnosticsScreen).isVisible = appBuildConfig.isDebug
        menu.findItem(R.id.dataScreen).isVisible = appBuildConfig.isDebug
        menu.findItem(R.id.customDnsServer).isVisible = appBuildConfig.isDebug

        vpnCachedState?.let { vpnState ->
            deviceShieldSwitch.quietlySetIsChecked(vpnState.state == VpnRunningState.ENABLED, enableAppTPSwitchListener)
            vpnCachedState = null
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.dataScreen -> {
                startActivity(VpnControllerActivity.intent(this)); true
            }
            R.id.diagnosticsScreen -> {
                startActivity(VpnDiagnosticsActivity.intent(this)); true
            }
            R.id.customDnsServer -> {
                val enabled = !item.isChecked
                viewModel.useCustomDnsServer(enabled)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private fun launchFeedback() {
        deviceShieldPixels.didSubmitReportIssuesFromTrackerActivity()
        reportBreakage.launch(ReportBreakageScreen.ListOfInstalledApps)
    }

    private fun launchAppSettings() {
        deviceShieldPixels.didSubmitReportIssuesFromTrackerActivity()
        reportBreakage.launch(ReportBreakageScreen.ListOfInstalledApps)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA"
        private const val ON_LAUNCHED_CALLED_SUCCESS = 0
        private const val MIN_ROWS_FOR_ALL_ACTIVITY = 6

        private const val REQUEST_ASK_VPN_PERMISSION = 101

        fun intent(
            context: Context,
            onLaunchCallback: ResultReceiver? = null
        ): Intent {
            return Intent(context, DeviceShieldTrackerActivity::class.java).apply {
                putExtra(RESULT_RECEIVER_EXTRA, onLaunchCallback)
            }
        }
    }
}
