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

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import dagger.android.AndroidInjection
import dagger.binding.TileServiceBingingKey
import javax.inject.Inject
import kotlinx.coroutines.*
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N)
class DeviceShieldTileService : TileService() {

    @Inject lateinit var deviceShieldPixels: DeviceShieldPixels
    @Inject lateinit var waitlistManager: TrackingProtectionWaitlistManager

    private var deviceShieldStatePollingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this, TileServiceBingingKey::class.java)
    }

    override fun onClick() {
        if (waitlistManager.didJoinBeta()) {
            respondToTile()
        } else {
            launchActivity(Class.forName("com.duckduckgo.app.settings.SettingsActivity"))
        }
    }

    private fun respondToTile() {
        if (TrackerBlockingVpnService.isServiceRunning(this)) {
            deviceShieldPixels.disableFromQuickSettingsTile()
            TrackerBlockingVpnService.stopService(this)
        } else {
            deviceShieldPixels.enableFromQuickSettingsTile()
            if (hasVpnPermission()) {
                startDeviceShield()
            } else {
                launchActivity(VpnPermissionRequesterActivity::class.java)
            }
        }
    }

    private fun launchActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivityAndCollapse(intent)
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
        deviceShieldStatePollingJob =
            serviceScope.launch {
                while (isActive) {
                    val tile = qsTile
                    tile?.let {
                        val isDeviceShieldEnabled =
                            TrackerBlockingVpnService.isServiceRunning(this@DeviceShieldTileService)
                        it.state = if (isDeviceShieldEnabled) STATE_ACTIVE else STATE_INACTIVE
                        it.updateTile()
                    }
                    delay(1_000)
                }
            }
    }

    private fun stopPollingDeviceShieldState() {
        deviceShieldStatePollingJob?.cancel()
    }

    private fun hasVpnPermission(): Boolean {
        return VpnService.prepare(this) == null
    }

    private fun startDeviceShield() {
        TrackerBlockingVpnService.startService(this)
    }
}

class VpnPermissionRequesterActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        startVpnIfAllowed()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> handleVpnPermissionResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        when (resultCode) {
            RESULT_CANCELED -> Timber.i("User cancelled and refused VPN permission")
            RESULT_OK -> {
                Timber.i("User granted VPN permission")
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
        TrackerBlockingVpnService.startService(this)
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, RC_REQUEST_VPN_PERMISSION)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RC_REQUEST_VPN_PERMISSION = 111
    }
}
