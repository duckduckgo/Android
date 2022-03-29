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

package com.duckduckgo.vpn.internal.feature.remote

import android.content.Context
import android.content.Intent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.vpn.internal.feature.InternalFeatureReceiver
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.reflect.KClass

private const val REMOTE_FEATURE = "remote-feature"
private const val ON = "on"
private const val OFF = "off"
private const val TYPE = "type"

class VpnRemoteFeatureReceiver(
    context: Context,
    receiver: (Intent) -> Unit
) : InternalFeatureReceiver(context, receiver) {

    override fun intentAction(): String = REMOTE_FEATURE

    companion object {

        fun <T : AppTpConfig> enableIntent(type: KClass<T>): Intent {
            return Intent(REMOTE_FEATURE).apply {
                putExtra(TYPE, type.nestedClassQualifiedName())
                putExtra(type.qualifiedName, ON)
            }
        }

        fun <T : AppTpConfig> disableIntent(type: KClass<T>): Intent {
            return Intent(REMOTE_FEATURE).apply {
                putExtra(TYPE, type.nestedClassQualifiedName())
                putExtra(type.qualifiedName, OFF)
            }
        }

        fun isOnIntent(intent: Intent): Boolean {
            return intent.getStringExtra(getType<AppTpConfig>(intent).qualifiedName)?.lowercase() == ON
        }

        fun isOffIntent(intent: Intent): Boolean {
            return intent.getStringExtra(getType<AppTpConfig>(intent).qualifiedName)?.lowercase() == OFF
        }

        fun <T : AppTpConfig> getType(intent: Intent): KClass<T> {
            return Class.forName(intent.getStringExtra(TYPE)!!).kotlin as KClass<T>
        }

        private fun KClass<*>.nestedClassQualifiedName(): String {
            val nestedClassName = qualifiedName?.substringAfterLast('.')
            val rootClassQualifiedName = qualifiedName?.substringBeforeLast('.')
            return "$rootClassQualifiedName$$nestedClassName"
        }
    }
}

@ContributesMultibinding(AppScope::class)
class VpnRemoteFeatureReceiverRegister @Inject constructor(
    private val context: Context,
    private val appTpFeatureConfig: AppTpFeatureConfig,
) : VpnServiceCallbacks {

    private var receiver: VpnRemoteFeatureReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("Debug receiver RemoteFeatureReceiver registered")

        receiver = VpnRemoteFeatureReceiver(context) { intent ->
            Timber.v("RemoteFeatureReceiver receive $intent")
            when {
                VpnRemoteFeatureReceiver.isOnIntent(intent) -> {
                    coroutineScope.launch {
                        appTpFeatureConfig.edit {
                            val config = appTpFeatureConfig.get(VpnRemoteFeatureReceiver.getType(intent))
                            config?.let { put(it.copy(isEnabled = true)) }
                        }
                    }
                }
                VpnRemoteFeatureReceiver.isOffIntent(intent) -> {
                    coroutineScope.launch {
                        appTpFeatureConfig.edit {
                            val config = appTpFeatureConfig.get(VpnRemoteFeatureReceiver.getType(intent))
                            config?.let { put(it.copy(isEnabled = false)) }
                        }
                    }
                }
                else -> Timber.w("RemoteFeatureReceiver unknown intent")
            }
        }.apply { register() }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        receiver?.unregister()
    }

    private fun AppTpConfig.copy(isEnabled: Boolean): AppTpConfig {
        return when (this) {
            is AppTpConfig.Ipv6Config -> this.copy(isEnabled = isEnabled)
            is AppTpConfig.BadHealthMitigationConfig -> this.copy(isEnabled = isEnabled)
            is AppTpConfig.NetworkSwitchHandlingConfig -> this.copy(isEnabled = isEnabled)
            is AppTpConfig.PrivateDnsConfig -> this.copy(isEnabled = isEnabled)
        }
    }
}
