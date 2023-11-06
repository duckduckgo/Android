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

package com.duckduckgo.networkprotection.internal.feature

import android.Manifest.permission.READ_PHONE_STATE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.launchApplicationInfoSettings
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQualityStore
import com.duckduckgo.networkprotection.impl.connectionclass.asConnectionQuality
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.internal.databinding.ActivityNetpInternalSettingsBinding
import com.duckduckgo.networkprotection.internal.feature.NetPEnvironmentSettingActivity.Companion.NetPEnvironmentSettingScreen
import com.duckduckgo.networkprotection.internal.feature.snooze.VpnDisableOnCall
import com.duckduckgo.networkprotection.internal.feature.system_apps.NetPSystemAppsExclusionListActivity
import com.duckduckgo.networkprotection.internal.network.NetPInternalMtuProvider
import com.duckduckgo.networkprotection.internal.network.netpDeletePcapFile
import com.duckduckgo.networkprotection.internal.network.netpGetPcapFile
import com.duckduckgo.networkprotection.internal.network.netpPcapFileHasContent
import com.duckduckgo.networkprotection.internal.rekey.DebugRekeyReceiver
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.remote_config.NetPServerRepository
import com.google.android.material.snackbar.Snackbar
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@InjectWith(ActivityScope::class)
class NetPInternalSettingsActivity : DuckDuckGoActivity() {

    @Inject lateinit var netPInternalFeatureToggles: NetPInternalFeatureToggles

    @Inject lateinit var netPInternalMtuProvider: NetPInternalMtuProvider

    @Inject lateinit var networkProtectionState: NetworkProtectionState

    @Inject lateinit var serverRepository: NetPServerRepository

    @Inject lateinit var netpRepository: NetworkProtectionRepository

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var connectionQualityStore: ConnectionQualityStore

    @Inject lateinit var netPGeoswitchingRepository: NetPGeoswitchingRepository

    @Inject lateinit var appBuildConfig: AppBuildConfig

    @Inject lateinit var vpnDisableOnCall: VpnDisableOnCall

    private val job = ConflatedJob()

    private val exportPcapFile = registerForActivityResult(ExportPcapContract()) { data ->
        data?.let { uri ->
            lifecycleScope.launch(dispatcherProvider.io()) {
                contentResolver.openOutputStream(uri)?.let { out ->
                    if (netpGetPcapFile().length() > 0) {
                        val input = FileInputStream(netpGetPcapFile())

                        input.copyTo(out)
                        out.flush()
                        out.close()
                        Snackbar.make(binding.root, "PCAP file exported successfully", Snackbar.LENGTH_LONG).show()
                    } else {
                        Snackbar.make(binding.root, "Error: Empty PCAP file", Snackbar.LENGTH_LONG).show()
                    }
                } ?: Snackbar.make(binding.root, "Error exporting PCAP file", Snackbar.LENGTH_LONG).show()
            }
        } ?: Snackbar.make(binding.root, "Error exporting PCAP file", Snackbar.LENGTH_LONG).show()
    }

    private val binding: ActivityNetpInternalSettingsBinding by viewBinding()

    private val mtuSizes: List<Int?> = listOf(1000, 1100, 1200, 1280, 1400, 1500, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        setupUiElementState()
        setupConfigSection()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun setupUiElementState() {
        job += lifecycleScope.launch {
            while (isActive) {
                val isEnabled = networkProtectionState.isEnabled()

                binding.snoozeWhileCalling.isEnabled = isEnabled
                binding.excludeSystemAppsToggle.isEnabled = isEnabled
                binding.dnsLeakProtectionToggle.isEnabled = isEnabled
                binding.netpPcapRecordingToggle.isEnabled = isEnabled
                binding.netpDevSettingHeaderPCAPDeleteItem.isEnabled = isEnabled && !netPInternalFeatureToggles.enablePcapRecording().isEnabled()
                binding.netpSharePcapFileItem.isEnabled = isEnabled && !netPInternalFeatureToggles.enablePcapRecording().isEnabled()
                binding.systemAppsItem.isEnabled = isEnabled && !netPInternalFeatureToggles.excludeSystemApps().isEnabled()
                binding.overrideMtuSelector.isEnabled = isEnabled
                binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
                binding.overrideServerBackendSelector.isEnabled = isEnabled
                binding.overrideServerBackendSelector.setSecondaryText("${serverRepository.getSelectedServer()?.name ?: AUTOMATIC}")
                binding.forceRekey.isEnabled = isEnabled
                if (isEnabled) {
                    netpRepository.clientInterface?.tunnelCidrSet?.joinToString(", ")?.let {
                        binding.internalIp.show()
                        binding.internalIp.setSecondaryText(it)
                    } ?: binding.internalIp.gone()
                    netpRepository.privateKey?.let {
                        "Device Public key: ${KeyPair(Key.fromBase64(it)).publicKey.toBase64()}".run {
                            if (netpRepository.lastPrivateKeyUpdateTimeInMillis != -1L) {
                                this + "\nLast updated ${formatter.format(netpRepository.lastPrivateKeyUpdateTimeInMillis)}"
                            } else {
                                this
                            }
                        }.also { subtitle ->
                            binding.forceRekey.setSecondaryText(subtitle)
                        }
                    }
                } else {
                    binding.internalIp.gone()
                }

                val (latency, quality) = withContext(dispatcherProvider.io()) {
                    val latency = connectionQualityStore.getConnectionLatency()
                    latency to latency.asConnectionQuality()
                }
                binding.connectionClass.setSecondaryText("${quality.emoji} $quality ($latency ms)")

                delay(1_000)
            }
        }
    }

    private fun setupConfigSection() {
        fun hasPhoneStatePermission(): Boolean {
            return if (appBuildConfig.sdkInt >= Build.VERSION_CODES.M) {
                ContextCompat.checkSelfPermission(
                    this,
                    READ_PHONE_STATE,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        lifecycleScope.launch {
            with(vpnDisableOnCall) {
                binding.snoozeWhileCalling.setIsChecked(this.isEnabled())
                binding.snoozeWhileCalling.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked && hasPhoneStatePermission()) {
                        vpnDisableOnCall.enable()
                    } else if (isChecked) {
                        binding.snoozeWhileCalling.setIsChecked(false)
                        if (shouldShowRequestPermissionRationale(READ_PHONE_STATE)) {
                            Snackbar.make(
                                binding.root,
                                getString(com.duckduckgo.networkprotection.internal.R.string.netpGrantPhonePermissionByline),
                                Snackbar.LENGTH_LONG,
                            ).setAction(
                                getString(com.duckduckgo.networkprotection.internal.R.string.netpGrantPhonePermissionAction),
                            ) {
                                // User denied the permission 2+ times
                                this@NetPInternalSettingsActivity.launchApplicationInfoSettings()
                            }.show()
                        } else {
                            requestPermissions(arrayOf(READ_PHONE_STATE), READ_PHONE_STATE.hashCode().absoluteValue)
                        }
                    } else {
                        binding.snoozeWhileCalling.setIsChecked(false)
                        vpnDisableOnCall.disable()
                    }
                }
            }
        }

        with(netPInternalFeatureToggles.excludeSystemApps()) {
            binding.excludeSystemAppsToggle.setIsChecked(this.isEnabled())
            binding.excludeSystemAppsToggle.setOnCheckedChangeListener { _, isChecked ->
                this.setEnabled(Toggle.State(enable = isChecked))
                networkProtectionState.restart()
            }
        }

        binding.overrideMtuSelector.setOnClickListener { showMtuSelectorMenu() }
        binding.overrideServerBackendSelector.setOnClickListener {
            lifecycleScope.launch {
                showServerSelectorMenu()
            }
        }
        binding.systemAppsItem.setOnClickListener {
            startActivity(NetPSystemAppsExclusionListActivity.intent(this))
        }

        with(netPInternalFeatureToggles.cloudflareDnsFallback()) {
            binding.dnsLeakProtectionToggle.setIsChecked(this.isEnabled())
            binding.dnsLeakProtectionToggle.setOnCheckedChangeListener { _, isChecked ->
                this.setEnabled(Toggle.State(enable = isChecked))
                networkProtectionState.restart()
            }
        }

        with(netPInternalFeatureToggles.enablePcapRecording()) {
            binding.netpPcapRecordingToggle.setIsChecked(this.isEnabled())
            binding.netpPcapRecordingToggle.setOnCheckedChangeListener { _, isChecked ->
                this.setEnabled(Toggle.State(enable = isChecked))
                networkProtectionState.restart()
            }
        }
        binding.netpDevSettingHeaderPCAPDeleteItem.setOnClickListener {
            if (this.netpDeletePcapFile()) {
                Snackbar.make(binding.root, "Pcap file deleted", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(binding.root, "Pcap file doesn't exist", Snackbar.LENGTH_LONG).show()
            }
        }
        binding.netpSharePcapFileItem.setOnClickListener {
            if (this.netpPcapFileHasContent()) {
                exportPcapFile.launch(null)
            } else {
                Snackbar.make(binding.root, "Pcap file doesn't exist", Snackbar.LENGTH_LONG).show()
            }
        }

        binding.forceRekey.setClickListener {
            lifecycleScope.launch {
                sendBroadcast(Intent(DebugRekeyReceiver.ACTION_FORCE_REKEY))
            }
        }

        binding.changeEnvironment.setClickListener {
            globalActivityStarter.start(this, NetPEnvironmentSettingScreen)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_PHONE_STATE.hashCode().absoluteValue -> {
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                vpnDisableOnCall.enable()
                binding.snoozeWhileCalling.setIsChecked(granted)
                if (!granted) {
                    logcat { "READ_PHONE_STATE permission denied" }
                }
            }
            else -> {}
        }
    }

    private fun showMtuSelectorMenu() {
        PopupMenu(this, binding.overrideMtuSelector).apply {
            mtuSizes.forEach {
                menu.add(it?.toString() ?: AUTOMATIC)
            }
            setOnMenuItemClickListener { menuItem ->
                val mtuSize = kotlin.runCatching { menuItem.title.toString().toInt() }.getOrNull()
                netPInternalMtuProvider.setMtu(mtuSize)
                binding.overrideMtuSelector.setSecondaryText("MTU size: ${netPInternalMtuProvider.getMtu()}")
                networkProtectionState.restart()

                true
            }
            setOnDismissListener { }
        }.show()
    }

    private suspend fun showServerSelectorMenu() {
        fun MenuItem.serverName(): String? {
            return if (title == AUTOMATIC) null else title.toString()
        }

        val hostnames = serverRepository.getServerNames()
        PopupMenu(this, binding.overrideServerBackendSelector).apply {
            (hostnames + AUTOMATIC).forEach { hostname ->
                menu.add(hostname)
            }

            setOnMenuItemClickListener {
                this@NetPInternalSettingsActivity.lifecycleScope.launch {
                    serverRepository.setSelectedServer(it.serverName())
                    binding.overrideServerBackendSelector.setSecondaryText("${serverRepository.getSelectedServer()?.name ?: AUTOMATIC}")
                    networkProtectionState.restart()
                    serverRepository.getSelectedServer()?.let {
                        netPGeoswitchingRepository.setUserPreferredLocation(
                            UserPreferredLocation(
                                countryCode = it.countryCode,
                                cityName = it.city,
                            ),
                        )
                    }
                }
                true
            }

            setOnDismissListener { }
        }.show()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NetPInternalSettingsActivity::class.java)
        }

        private val formatter by lazy {
            SimpleDateFormat("MMM dd, yyyy HH:mm aaa", Locale.getDefault())
        }

        private const val AUTOMATIC = "Automatic"
    }
}
