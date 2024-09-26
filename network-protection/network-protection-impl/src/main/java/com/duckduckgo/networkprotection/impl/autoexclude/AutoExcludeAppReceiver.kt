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
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@SingleInstanceIn(VpnScope::class)
@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class AutoExcludeAppReceiver @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val localConfig: NetPSettingsLocalConfig,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
    private val applicationContext: Context,
    @AppCoroutineScope private var coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
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
        if (localConfig.autoExcludeBrokenApps().isEnabled()) {
            logcat { "Auto exclude enabled, checking if $packageName is in auto exclude list" }
            if (autoExcludeAppsRepository.isAppMarkedAsIncompatible(packageName)) {
                logcat { "Newly installed package $packageName is in auto exclude list" }
                networkProtectionState.restart()
            } else {
                logcat { "Newly installed package $packageName not in auto exclude list" }
            }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "Auto exclude receiver started" }
        kotlin.runCatching { applicationContext.unregisterReceiver(this) }

        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }.run {
            applicationContext.registerExportedReceiver(this@AutoExcludeAppReceiver, this)
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
