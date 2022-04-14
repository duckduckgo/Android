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
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.health.AppHealthMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.vpn.internal.databinding.ActivityVpnInternalSettingsBinding
import com.duckduckgo.vpn.internal.feature.bugreport.VpnBugReporter
import com.duckduckgo.vpn.internal.feature.logs.DebugLoggingReceiver
import com.duckduckgo.vpn.internal.feature.logs.TimberExtensions
import com.duckduckgo.vpn.internal.feature.remote.VpnRemoteFeatureReceiver
import com.duckduckgo.vpn.internal.feature.rules.ExceptionRulesDebugActivity
import com.duckduckgo.vpn.internal.feature.trackers.DeleteTrackersDebugReceiver
import com.duckduckgo.vpn.internal.feature.transparency.TransparencyModeDebugReceiver
import com.google.android.material.snackbar.Snackbar
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
        setupViewDiagnosticsView()
        setupBadHealthMonitoring()
        setupConfigSection()
        setupUiElementsState()
    }

    override fun onDestroy() {
        super.onDestroy()
        transparencyModeDebugReceiver?.let { it.unregister() }
    }

    private fun setupUiElementsState() {
        vpnStateMonitor.getStateFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .map { it.state == VpnStateMonitor.VpnRunningState.ENABLED }
            .onEach { isEnabled ->
                binding.ipv6SupportToggle.isEnabled = isEnabled
                binding.privateDnsToggle.isEnabled = isEnabled
                binding.badHealthMonitorToggle.isEnabled = isEnabled
                binding.badHealthMitigationToggle.isEnabled = isEnabled
                binding.vpnUnderlyingNetworksToggle.isEnabled = isEnabled
                binding.vpnAlwaysSetDNSToggle.isEnabled = isEnabled
                binding.setActiveNetworkDnsToggle.isEnabled = isEnabled
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

    private fun setupViewDiagnosticsView() {
        binding.viewDiagnostics.setOnClickListener {
            val i = Intent().also { it.setClassName(packageName, "dummy.ui.VpnDiagnosticsActivity") }
            startActivity(i)
        }
    }

    private fun setupBugReport() {
        binding.apptpBugreport.setTitle("Generate AppTP bug report")
        binding.apptpBugreport.setIsLoading(false)
        binding.apptpBugreport.setOnClickListener {
            lifecycleScope.launch {
                binding.apptpBugreport.setIsLoading(true)
                val bugreport = vpnBugReporter.generateBugReport()
                shareBugReport(bugreport)
                binding.apptpBugreport.setIsLoading(false)
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
            binding.transparencyModeToggle.setOnCheckedChangeListener(null)
            if (TransparencyModeDebugReceiver.isTurnOnIntent(it)) {
                binding.transparencyModeToggle.isChecked = true
            } else if (TransparencyModeDebugReceiver.isTurnOffIntent(it)) {
                binding.transparencyModeToggle.isChecked = false
            }
            binding.transparencyModeToggle.setOnCheckedChangeListener(transparencyToggleListener)
        }.apply { register() }

        binding.transparencyModeToggle.setOnCheckedChangeListener(transparencyToggleListener)
    }

    private fun setupBadHealthMonitoring() {
        binding.badHealthMonitorToggle.isChecked = appHealthMonitor.isMonitoringStarted()
        binding.badHealthMonitorToggle.setOnCheckedChangeListener(badHealthMonitoringToggleListener)

        binding.badHealthMitigationToggle.isChecked = appTpConfig.isEnabled(AppTpSetting.BadHealthMitigation)
        binding.badHealthMitigationToggle.setOnCheckedChangeListener(badHealthMitigationFeatureToggle)
    }

    private fun setupConfigSection() {
        with(AppTpSetting.Ipv6Support) {
            binding.ipv6SupportToggle.isChecked = appTpConfig.isEnabled(this)
            binding.ipv6SupportToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.PrivateDnsSupport) {
            binding.privateDnsToggle.isChecked = appTpConfig.isEnabled(this)
            binding.privateDnsToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.NetworkSwitchHandling) {
            binding.vpnUnderlyingNetworksToggle.isChecked = appTpConfig.isEnabled(this)
            binding.vpnUnderlyingNetworksToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.AlwaysSetDNS) {
            binding.vpnAlwaysSetDNSToggle.isChecked = appTpConfig.isEnabled(this)
            binding.vpnAlwaysSetDNSToggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    sendBroadcast(VpnRemoteFeatureReceiver.enableIntent(this))
                } else {
                    sendBroadcast(VpnRemoteFeatureReceiver.disableIntent(this))
                }
            }
        }

        with(AppTpSetting.SetActiveNetworkDns) {
            binding.setActiveNetworkDnsToggle.isChecked = appTpConfig.isEnabled(this)
            binding.setActiveNetworkDnsToggle.setOnCheckedChangeListener { _, isChecked ->
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
            binding.debugLoggingToggle.setOnCheckedChangeListener(null)
            if (DebugLoggingReceiver.isLoggingOnIntent(intent)) {
                binding.debugLoggingToggle.isChecked = true
            } else if (DebugLoggingReceiver.isLoggingOffIntent(intent)) {
                binding.debugLoggingToggle.isChecked = false
            }
            binding.debugLoggingToggle.setOnCheckedChangeListener(debugLoggingToggleListener)
        }.apply { register() }

        // initial state
        binding.debugLoggingToggle.isChecked = TimberExtensions.isLoggingEnabled()

        // listener
        binding.debugLoggingToggle.setOnCheckedChangeListener(debugLoggingToggleListener)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnInternalSettingsActivity::class.java)
        }
    }
}
