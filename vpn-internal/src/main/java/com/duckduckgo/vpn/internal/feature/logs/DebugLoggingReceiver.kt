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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.VpnStopReason
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.vpn.internal.feature.InternalFeatureReceiver
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject

class DebugLoggingReceiver(
    context: Context,
    receiver: (Intent) -> Unit
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

@ContributesMultibinding(AppScope::class)
class DebugLoggingReceiverRegister @Inject constructor(
    private val context: Context
) : VpnServiceCallbacks {

    private var receiver: DebugLoggingReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("Debug receiver DebugLoggingReceiver registered")

        receiver = DebugLoggingReceiver(context) { intent ->
            when {
                DebugLoggingReceiver.isLoggingOnIntent(intent) -> {
                    TimberExtensions.enableLogging()
                }
                DebugLoggingReceiver.isLoggingOffIntent(intent) -> {
                    TimberExtensions.disableLogging()
                }
                else -> Timber.w("Debug receiver DebugLoggingReceiver unknown intent")
            }
        }.apply { register() }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        receiver?.unregister()
    }
}
