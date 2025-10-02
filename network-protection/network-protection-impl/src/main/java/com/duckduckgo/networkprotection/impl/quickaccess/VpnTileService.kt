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

package com.duckduckgo.networkprotection.impl.quickaccess

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import android.service.quicksettings.TileService
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenAndEnable
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.quickaccess.VpnTileStateProvider.VpnTileState
import dagger.android.AndroidInjection
import dagger.binding.VpnTileServiceBindingKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("NoHardcodedCoroutineDispatcher")
@InjectWith(
    scope = ServiceScope::class,
    bindingKey = VpnTileServiceBindingKey::class,
)
class VpnTileService : TileService() {
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var vpnTileStateProvider: VpnTileStateProvider

    @Inject
    lateinit var networkProtectionState: NetworkProtectionState

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var networkProtectionPixels: NetworkProtectionPixels

    private var statePollingJob = ConflatedJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this, VpnTileServiceBindingKey::class.java)
    }

    @SuppressLint("NewApi", "StartActivityAndCollapseDeprecated") // IDE doesn't get we use appBuildConfig
    override fun onClick() {
        serviceScope.launch(dispatcherProvider.io()) {
            if (networkProtectionState.isRunning()) {
                networkProtectionState.stop()
                networkProtectionPixels.reportVpnDisabledFromQuickSettingsTile()
            } else {
                networkProtectionPixels.reportVpnEnabledFromQuickSettingsTile()
                if (hasVpnPermission()) {
                    networkProtectionState.start()
                } else {
                    globalActivityStarter.startIntent(this@VpnTileService, NetworkProtectionManagementScreenAndEnable(true))?.apply {
                        this.addFlags(FLAG_ACTIVITY_NEW_TASK)
                    }?.also {
                        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val pendingIntent = PendingIntent.getActivity(
                                this@VpnTileService,
                                0,
                                it,
                                PendingIntent.FLAG_IMMUTABLE,
                            )
                            startActivityAndCollapse(pendingIntent)
                        } else {
                            startActivityAndCollapse(it)
                        }
                    }
                }
            }
        }
    }

    private fun hasVpnPermission(): Boolean {
        return VpnService.prepare(this) == null
    }

    override fun onStartListening() {
        super.onStartListening()
        pollVPNState()
    }

    override fun onStopListening() {
        statePollingJob.cancel()
        super.onStopListening()
    }

    private fun pollVPNState() {
        statePollingJob += serviceScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                val tile = qsTile
                tile?.let {
                    it.state = when (vpnTileStateProvider.getVpnTileState()) {
                        VpnTileState.CONNECTED -> STATE_ACTIVE
                        VpnTileState.DISCONNECTED -> STATE_INACTIVE
                        VpnTileState.UNAVAILABLE -> STATE_UNAVAILABLE
                    }
                    it.updateTile()
                }
                delay(1_000)
            }
        }
    }
}
