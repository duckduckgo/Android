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

package com.duckduckgo.mobile.android.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class
)
@SingleInstanceIn(VpnScope::class)
class RestartReceiver @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : BroadcastReceiver(), VpnServiceCallbacks {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("action")?.lowercase() == "restart") {
            coroutineScope.launch {
                TrackerBlockingVpnService.restartVpnService(context)
            }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (appBuildConfig.isInternalBuild()) {
            Timber.v("Starting vpn-service receiver")
            unregister()
            context.registerReceiver(this, IntentFilter("vpn-service"))
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        unregister()
    }

    private fun unregister() {
        kotlin.runCatching { context.unregisterReceiver(this) }
    }
}
