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

package com.duckduckgo.mobile.android.vpn.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import dagger.android.AndroidInjection
import dagger.binding.TileServiceBingingKey
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.logcat

@Suppress("NoHardcodedCoroutineDispatcher")
// We don't use the DeviceShieldTileService::class as binding key because TileService (Android) class does not
// exist in all APIs, and so using it DeviceShieldTileService::class as key would compile but immediately crash
// at startup when Java class loader tries to resolve the TileService::class upon Dagger setup
@InjectWith(
    scope = ServiceScope::class,
    bindingKey = TileServiceBingingKey::class,
)
class DeviceShieldTileService : TileService() {

    @Inject lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var appBuildConfig: AppBuildConfig

    private var deviceShieldStatePollingJob = ConflatedJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this, TileServiceBingingKey::class.java)
    }

    override fun onClick() {
        respondToTile()
    }

    private fun respondToTile() {
        serviceScope.launch(dispatcherProvider.io()) {
            if (vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)) {
                deviceShieldPixels.disableFromQuickSettingsTile()
                vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)
            } else {
                deviceShieldPixels.enableFromQuickSettingsTile()
                if (hasVpnPermission()) {
                    startDeviceShield()
                } else {
                    launchActivity()
                }
            }
        }
    }

    @SuppressLint("NewApi", "StartActivityAndCollapseDeprecated") // IDE doesn't get we use appBuildConfig
    private fun launchActivity() {
        val intent = Intent(this, VpnPermissionRequesterActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        pollDeviceShieldState()
    }

    override fun onStopListening() {
        stopPollingDeviceShieldState()
        super.onStopListening()
    }

    private fun pollDeviceShieldState() {
        deviceShieldStatePollingJob +=
            serviceScope.launch(dispatcherProvider.io()) {
                while (isActive) {
                    val tile = qsTile
                    tile?.let {
                        val isDeviceShieldEnabled = vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)
                        it.state = if (isDeviceShieldEnabled) STATE_ACTIVE else STATE_INACTIVE
                        it.updateTile()
                    }
                    delay(1_000)
                }
            }
    }

    private fun stopPollingDeviceShieldState() {
        deviceShieldStatePollingJob.cancel()
    }

    private fun hasVpnPermission(): Boolean {
        return VpnService.prepare(this) == null
    }

    private suspend fun startDeviceShield() {
        vpnFeaturesRegistry.registerFeature(AppTpVpnFeature.APPTP_VPN)
    }
}

@InjectWith(ActivityScope::class)
class VpnPermissionRequesterActivity : DuckDuckGoActivity() {
    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override fun onStart() {
        super.onStart()
        startVpnIfAllowed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> handleVpnPermissionResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        when (resultCode) {
            RESULT_CANCELED -> logcat { "User cancelled and refused VPN permission" }
            RESULT_OK -> {
                logcat { "User granted VPN permission" }
                startDeviceShield()
            }
        }
        finish()
    }

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(this)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> {
                startDeviceShield()
                finish()
            }

            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
        }
    }

    private fun startDeviceShield() {
        lifecycleScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.registerFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, RC_REQUEST_VPN_PERMISSION)
    }

    private sealed class VpnPermissionStatus {
        data object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RC_REQUEST_VPN_PERMISSION = 111
    }
}
