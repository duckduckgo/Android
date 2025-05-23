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

package com.duckduckgo.vpn.internal.feature.logs

import android.content.Context
import android.content.Intent
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.feature.AppTpLocalFeature
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.vpn.internal.feature.InternalFeatureReceiver
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import logcat.logcat

class DebugLoggingReceiver(
    context: Context,
    receiver: (Intent) -> Unit,
) : InternalFeatureReceiver(context, receiver) {

    override fun intentAction(): String = ACTION

    companion object {
        private const val ACTION = "logging"

        fun turnOnIntent(): Intent {
            return Intent(ACTION).apply {
                putExtra("turn", "on")
            }
        }

        fun turnOffIntent(): Intent {
            return Intent(ACTION).apply {
                putExtra("turn", "off")
            }
        }

        fun isLoggingOnIntent(intent: Intent): Boolean {
            return intent.getStringExtra("turn")?.lowercase() == "on"
        }

        fun isLoggingOffIntent(intent: Intent): Boolean {
            return intent.getStringExtra("turn")?.lowercase() == "off"
        }
    }
}

@ContributesMultibinding(VpnScope::class)
class DebugLoggingReceiverRegister @Inject constructor(
    private val context: Context,
    private val appTpLocalFeature: AppTpLocalFeature,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {

    private var receiver: DebugLoggingReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "Debug receiver DebugLoggingReceiver registered" }

        receiver = DebugLoggingReceiver(context) { intent ->
            when {
                DebugLoggingReceiver.isLoggingOnIntent(intent) -> {
                    appTpLocalFeature.verboseLogging().setRawStoredState(Toggle.State(enable = true))
                    LoggingExtensions.enableLogging()

                    // To propagate changes to NetGuard, reconfigure the VPN
                    Intent("vpn-service").apply {
                        putExtra("action", "restart")
                    }.also {
                        context.sendBroadcast(it)
                    }
                }
                DebugLoggingReceiver.isLoggingOffIntent(intent) -> {
                    appTpLocalFeature.verboseLogging().setRawStoredState(Toggle.State(enable = false))
                    LoggingExtensions.disableLogging()

                    // To propagate changes to NetGuard, reconfigure the VPN
                    Intent("vpn-service").apply {
                        putExtra("action", "restart")
                    }.also {
                        context.sendBroadcast(it)
                    }
                }
                else -> logcat { "Debug receiver DebugLoggingReceiver unknown intent" }
            }
        }.apply { register() }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        receiver?.unregister()
    }
}
