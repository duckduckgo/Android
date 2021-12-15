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

package com.duckduckgo.vpn.internal.feature.transparency

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.vpn.internal.feature.InternalFeatureReceiver
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * This receiver allows to enable/disable transparency mode. Where
 * traffic passes through the VPN but no tracker is blocked.
 *
 * $ adb shell am broadcast -a transparency --es turn <on/off>
 *
 * where `--es turn <on/off> to enable/disable VPN transparency mode
 */
class TransparencyModeDebugReceiver(
    context: Context,
    receiver: (Intent) -> Unit
) : InternalFeatureReceiver(context, receiver) {

    override fun intentAction(): String = ACTION

    companion object {

        private const val ACTION = "transparency"
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

        fun isTurnOnIntent(intent: Intent): Boolean {
            return intent.getStringExtra("turn")?.lowercase() == "on"
        }
        fun isTurnOffIntent(intent: Intent): Boolean {
            return intent.getStringExtra("turn")?.lowercase() == "off"
        }
    }
}

@ContributesMultibinding(AppScope::class)
class ExceptionRulesDebugReceiverRegister @Inject constructor(
    private val context: Context,
    private val trackerDetectorInterceptor: TransparencyTrackerDetectorInterceptor
) : VpnServiceCallbacks {

    private val stateRefresherJob = ConflatedJob()
    private var receiver: TransparencyModeDebugReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.i("Debug receiver TransparencyModeDebugReceiver registered")

        unregisterReceiver()

        receiver = TransparencyModeDebugReceiver(context) { intent ->
            when {
                TransparencyModeDebugReceiver.isTurnOnIntent(intent) -> {
                    Timber.i("Debug receiver TransparencyModeDebugReceiver turning ON transparency mode")
                    trackerDetectorInterceptor.setEnable(true)
                }
                TransparencyModeDebugReceiver.isTurnOffIntent(intent) -> {
                    Timber.i("Debug receiver TransparencyModeDebugReceiver turning OFF transparency mode")
                    trackerDetectorInterceptor.setEnable(false)
                }
                else -> {
                    Timber.v("Debug receiver TransparencyModeDebugReceiver unknown intent")
                }
            }
        }.apply { register() }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        Timber.i("Debug receiver TransparencyModeDebugReceiver turning OFF transparency mode")
        trackerDetectorInterceptor.setEnable(false)
        stateRefresherJob.cancel()
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        receiver?.unregister()
    }
}
