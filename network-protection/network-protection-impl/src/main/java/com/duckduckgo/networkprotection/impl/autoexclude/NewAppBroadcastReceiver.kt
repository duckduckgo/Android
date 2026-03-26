/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.registerExportedReceiver
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(VpnScope::class)
@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class NewAppBroadcastReceiver @Inject constructor(
    private val applicationContext: Context,
    @AppCoroutineScope private var coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val appTrackingProtection: AppTrackingProtection,
) : BroadcastReceiver(), VpnServiceCallbacks {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED -> intent.data?.schemeSpecificPart?.let {
                coroutineScope.launch(dispatcherProvider.io()) {
                    restartVpn(it)
                }
            }
        }
    }

    private suspend fun restartVpn(packageName: String) {
        if (networkProtectionState.isEnabled() && networkProtectionState.getExcludedApps().contains(packageName)) {
            logcat { "Newly installed package $packageName is in NetP exclusion list, disabling/re-enabling vpn" }
            networkProtectionState.restart()
        } else if (appTrackingProtection.isEnabled() && appTrackingProtection.getExcludedApps().contains(packageName)) {
            logcat { "Newly installed package $packageName is in AppTP exclusion list, disabling/re-enabling vpn" }
            appTrackingProtection.restart()
        } else {
            logcat { "Newly installed package $packageName not in any exclusion list" }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "Auto exclude receiver started" }
        kotlin.runCatching { applicationContext.unregisterReceiver(this) }

        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }.run {
            applicationContext.registerExportedReceiver(this@NewAppBroadcastReceiver, this)
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        logcat { "Auto exclude receiver stopped" }
        kotlin.runCatching { applicationContext.unregisterReceiver(this) }
    }
}
