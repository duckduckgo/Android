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

package com.duckduckgo.vpn.internal.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.blocklist.AppTrackerListUpdateWorker
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.health.AppHealthMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.vpn.internal.databinding.ActivityVpnInternalSettingsBinding
import com.duckduckgo.vpn.internal.feature.bugreport.VpnBugReporter
import com.duckduckgo.vpn.internal.feature.logs.DebugLoggingReceiver
import com.duckduckgo.vpn.internal.feature.logs.TimberExtensions
import com.duckduckgo.vpn.internal.feature.remote.VpnRemoteFeatureReceiver
import com.duckduckgo.vpn.internal.feature.rules.ExceptionRulesDebugActivity
import com.duckduckgo.vpn.internal.feature.trackers.DeleteTrackersDebugReceiver
import com.duckduckgo.vpn.internal.feature.transparency.TransparencyModeDebugReceiver
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

class VpnInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var vpnBugReporter: VpnBugReporter

    @Inject
    lateinit var appHealthMonitor: AppHealthMonitor

    @Inject
    lateinit var appTpConfig: AppTpFeatureConfig

    @Inject
    lateinit var vpnStateMonitor: VpnStateMonitor

    @Inject
    lateinit var appTrackerRepository: AppTrackerRepository

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var workManager: WorkManager

    private val binding: ActivityVpnInternalSettingsBinding by viewBinding()
    private var transparencyModeDebugReceiver: TransparencyModeDebugReceiver? = null
    private var debugLoggingReceiver: DebugLoggingReceiver? = null

    private val transparencyToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        if (toggleState) {
            TransparencyModeDebugReceiver.turnOnIntent()
        } else {
            TransparencyModeDebugReceiver.turnOffIntent()
        }.also { sendBroadcast(it) }
    }

    private val badHealthMonitoringToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        if (toggleState) {
            appHealthMonitor.startMonitoring()
        } else {
            appHealthMonitor.stopMonitoring()
        }
    }

    private val badHealthMitigationFeatureToggle = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        if (toggleState) {
            sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(AppTpSetting.BadHealthMitigation))
        } else {
            sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(AppTpSetting.BadHealthMitigation))
        }
    }

    private val debugLoggingToggleListener = CompoundButton.OnCheckedChangeListener { _, toggleState ->
        if (toggleState) {
            DebugLoggingReceiver.turnOnIntent()
        } else {
            DebugLoggingReceiver.turnOffIntent()
        }.also { sendBroadcast(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupTransparencyMode()
        setupAppTrackerExceptionRules()
        setupDebugLogging()
        setupBugReport()
        setupDeleteTrackingHistory()
        setupForceUpdateBlocklist()
        setupViewDiagnosticsView()
        setupBadHealthMonitoring()
        setupConfigSection()
        setupUiElementsState()
        setupAppProtectionSection()
    }

    override fun onDestroy() {
        super.onDestroy()
        transparencyModeDebugReceiver?.unregister()
    }

    private fun setupAppProtectionSection() {
        appTrackerRepository.getAppExclusionListFlow()
            .combine(appTrackerRepository.getManualAppExclusionListFlow()) { exclusionList, manualList ->
                val mappedExclusionList = exclusionList.map { it.packageId }
                val mappedManualExclusionList = manualList.map { it.packageId }

                val canProtect = mappedManualExclusionList.isEmpty() ||
                    !mappedManualExclusionList.zip(mappedExclusionList).all { (one, other) -> one == other }
                val canRestoreDefaults = mappedManualExclusionList.isNotEmpty()

                canProtect to canRestoreDefaults
            }
            .flowOn(Dispatchers.IO)
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                val canProtect = it.first
                val canRestoreDefaults = it.second

                binding.restoreDefaultAppProtections.isEnabled = canRestoreDefaults
                binding.protectAllApps.isEnabled = canProtect
            }
            .flowOn(Dispatchers.Main)
            .launchIn(lifecycleScope)

        binding.protectAllApps.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                for (excludedPackage in appTrackerRepository.getAppExclusionList()) {
                    appTrackerRepository.manuallyEnabledApp(excludedPackage.packageId)
                }
                vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
            }
        }

        binding.restoreDefaultAppProtections.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                appTrackerRepository.restoreDefaultProtectedList()
                vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
            }
        }
    }

    private fun setupUiElementsState() {
        vpnStateMonitor.getStateFlow(AppTpVpnFeature.APPTP_VPN)
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .map { it.state == VpnStateMonitor.VpnRunningState.ENABLED }
            .onEach { isEnabled ->
                binding.ipv6SupportToggle.isEnabled = isEnabled
                binding.privateDnsToggle.isEnabled = isEnabled
                binding.badHealthMonitorToggle.isEnabled = isEnabled
                binding.badHealthMitigationToggle.isEnabled = isEnabled
                binding.vpnInterceptDnsTrafficToggle.isEnabled = isEnabled
                binding.vpnAlwaysSetDNSToggle.isEnabled = isEnabled
                binding.vpnConnectivityChecksToggle.isEnabled = isEnabled
                binding.debugLoggingToggle.isEnabled = isEnabled
                binding.transparencyModeToggle.isEnabled = isEnabled
                binding.settingsInfo.isVisible = !isEnabled
            }
            .launchIn(lifecycleScope)
    }

    private fun setupDeleteTrackingHistory() {
        binding.deleteTrackingHistory.setOnClickListener {
            sendBroadcast(DeleteTrackersDebugReceiver.createIntent())
            Snackbar.make(binding.root, "Tracking history deleted", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupForceUpdateBlocklist() {
        binding.forceUpdateBlocklist.setOnClickListener {
            val workerRequest =
                OneTimeWorkRequestBuilder<AppTrackerListUpdateWorker>().build()
            workManager.enqueue(workerRequest)
            Snackbar.make(binding.root, "Blocklist downloading...", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupViewDiagnosticsView() {
        binding.viewDiagnostics.setOnClickListener {
            val i = Intent().also { it.setClassName(packageName, "dummy.ui.VpnDiagnosticsActivity") }
            startActivity(i)
        }
    }

    private fun setupBugReport() {
        binding.apptpBugreport.setOnClickListener {
            Snackbar.make(binding.root, "Generating AppTP Bug Report", Snackbar.LENGTH_LONG).show()
            lifecycleScope.launch {
                val bugreport = vpnBugReporter.generateBugReport()
                shareBugReport(bugreport)
            }
        }
    }

    private fun shareBugReport(report: String) {
        Snackbar.make(binding.root, "AppTP bug report generated", Snackbar.LENGTH_INDEFINITE)
            .setAction("Share") {
                Intent(Intent.ACTION_SEND).run {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                    startActivity(Intent.createChooser(this, "Share AppTP bug report"))
                }
            }.show()
    }

    private fun setupAppTrackerExceptionRules() {
        binding.exceptionRules.setOnClickListener {
            startActivity(ExceptionRulesDebugActivity.intent(this))
        }
    }

    private fun setupTransparencyMode() {

        // we use the same receiver as it makes IPC much easier
        transparencyModeDebugReceiver = TransparencyModeDebugReceiver(this) {
            // avoid duplicating broadcast intent when toggle changes state
            if (TransparencyModeDebugReceiver.isTurnOnIntent(it)) {
                binding.transparencyModeToggle.quietlySetIsChecked(true, transparencyToggleListener)
            } else if (TransparencyModeDebugReceiver.isTurnOffIntent(it)) {
                binding.transparencyModeToggle.quietlySetIsChecked(false, transparencyToggleListener)
            }
        }.apply { register() }

        binding.transparencyModeToggle.setOnCheckedChangeListener(transparencyToggleListener)
    }

    private fun setupBadHealthMonitoring() {
        binding.badHealthMonitorToggle.setIsChecked(appHealthMonitor.isMonitoringStarted())
        binding.badHealthMonitorToggle.setOnCheckedChangeListener(badHealthMonitoringToggleListener)

        binding.badHealthMitigationToggle.setIsChecked(appTpConfig.isEnabled(AppTpSetting.BadHealthMitigation))
        binding.badHealthMitigationToggle.setOnCheckedChangeListener(badHealthMitigationFeatureToggle)
    }

    private fun setupConfigSection() {
        with(AppTpSetting.Ipv6Support) {
            binding.ipv6SupportToggle.setIsChecked(appTpConfig.isEnabled(this))
            binding.ipv6SupportToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.PrivateDnsSupport) {
            binding.privateDnsToggle.setIsChecked(appTpConfig.isEnabled(this))
            binding.privateDnsToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.InterceptDnsTraffic) {
            binding.vpnInterceptDnsTrafficToggle.setIsChecked(appTpConfig.isEnabled(this))
            binding.vpnInterceptDnsTrafficToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.AlwaysSetDNS) {
            binding.vpnAlwaysSetDNSToggle.setIsChecked(appTpConfig.isEnabled(this))
            binding.vpnAlwaysSetDNSToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.ConnectivityChecks) {
            binding.vpnConnectivityChecksToggle.setIsChecked(appTpConfig.isEnabled(this))
            binding.vpnConnectivityChecksToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }
    }

    private fun setupDebugLogging() {
        debugLoggingReceiver = DebugLoggingReceiver(this) { intent ->
            if (DebugLoggingReceiver.isLoggingOnIntent(intent)) {
                binding.debugLoggingToggle.quietlySetIsChecked(true, debugLoggingToggleListener)
            } else if (DebugLoggingReceiver.isLoggingOffIntent(intent)) {
                binding.debugLoggingToggle.quietlySetIsChecked(false, debugLoggingToggleListener)
            }
        }.apply { register() }

        // initial state
        binding.debugLoggingToggle.setIsChecked(TimberExtensions.isLoggingEnabled())

        // listener
        binding.debugLoggingToggle.setOnCheckedChangeListener(debugLoggingToggleListener)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnInternalSettingsActivity::class.java)
        }
    }
}
