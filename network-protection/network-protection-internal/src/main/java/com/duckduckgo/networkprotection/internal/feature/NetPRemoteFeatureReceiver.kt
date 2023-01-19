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

package com.duckduckgo.networkprotection.internal.feature

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

private const val REMOTE_FEATURE = "netp-remote-feature"
private const val ON = "on"
private const val OFF = "off"
private const val TYPE = "type"

class VpnRemoteFeatureReceiver(
    context: Context,
    receiver: (Intent) -> Unit,
) : InternalFeatureReceiver(context, receiver) {

    override fun intentAction(): String = REMOTE_FEATURE

    companion object {

        fun enableIntent(appTpSetting: NetPSetting): Intent {
            return Intent(REMOTE_FEATURE).apply {
                putExtra(TYPE, appTpSetting.value)
                putExtra(appTpSetting.value, ON)
            }
        }

        fun disableIntent(appTpSetting: NetPSetting): Intent {
            return Intent(REMOTE_FEATURE).apply {
                putExtra(TYPE, appTpSetting.value)
                putExtra(appTpSetting.value, OFF)
            }
        }

        fun isOnIntent(intent: Intent): Boolean {
            return intent.getStringExtra(settingName(intent).value)?.lowercase() == ON
        }

        fun isOffIntent(intent: Intent): Boolean {
            return intent.getStringExtra(settingName(intent).value)?.lowercase() == OFF
        }

        fun settingName(intent: Intent): SettingName {
            return SettingName { intent.getStringExtra(TYPE).orEmpty() }
        }
    }
}

@ContributesMultibinding(VpnScope::class)
class NetPRemoteFeatureReceiverRegister @Inject constructor(
    private val context: Context,
    private val netPFeatureConfig: NetPFeatureConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : VpnServiceCallbacks {

    private var receiver: VpnRemoteFeatureReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "Debug receiver RemoteFeatureReceiver registered" }

        receiver = VpnRemoteFeatureReceiver(context) { intent ->
            logcat { "RemoteFeatureReceiver receive $intent" }
            when {
                VpnRemoteFeatureReceiver.isOnIntent(intent) -> {
                    coroutineScope.launch {
                        netPFeatureConfig.edit {
                            setEnabled(VpnRemoteFeatureReceiver.settingName(intent), true, isManualOverride = true)
                        }
                        withContext(dispatcherProvider.main()) {
                            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
                        }
                    }
                }
                VpnRemoteFeatureReceiver.isOffIntent(intent) -> {
                    coroutineScope.launch {
                        netPFeatureConfig.edit {
                            setEnabled(VpnRemoteFeatureReceiver.settingName(intent), false, isManualOverride = true)
                        }
                        withContext(dispatcherProvider.main()) {
                            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
                        }
                    }
                }
                else -> logcat(LogPriority.WARN) { "RemoteFeatureReceiver unknown intent" }
            }
        }.apply { register() }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStateMonitor.VpnStopReason,
    ) {
        receiver?.unregister()
    }
}
