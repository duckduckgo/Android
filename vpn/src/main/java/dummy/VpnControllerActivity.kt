/*
 * Copyright (c) 2020 DuckDuckGo
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

package dummy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.vpn.PassthroughVpnService
import com.duckduckgo.mobile.android.vpn.R
import timber.log.Timber

class VpnControllerActivity : AppCompatActivity(R.layout.activity_vpn_controller) {

    private lateinit var vpnRunningToggleButton: ToggleButton
    private lateinit var vpnPermissionTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))
        setViewReferences()
        configureUiHandlers()
    }

    private fun setViewReferences() {
        vpnRunningToggleButton = findViewById(R.id.vpnRunningButton)
        vpnPermissionTextView = findViewById(R.id.vpnPermissionStatus)
    }

    private fun configureUiHandlers() {
        vpnRunningToggleButton.setOnCheckedChangeListener { _, enabled ->
            Timber.i("Toggle changed. enabled=$enabled")

            if (enabled) {
                startVpnIfAllowed()
            } else {
                stopVpn()
            }
        }
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> {
                Timber.v("This app already has permissions to be VPN app")
                startVpn()
            }
            is VpnPermissionStatus.Denied -> {
                Timber.v("VPN permission not granted")
                obtainVpnRequestPermission(permissionStatus.intent)
            }
        }
    }

    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, RC_REQUEST_VPN_PERMISSION)
    }

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(this)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> {
                handleVpnPermissionResult(resultCode)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_CANCELED -> Timber.i("User cancelled and refused VPN permission")
            Activity.RESULT_OK -> {
                Timber.i("User granted VPN permission")
                startVpn()
            }
        }
    }

    private fun startVpn() {
        startService(PassthroughVpnService.startIntent(this))
    }

    private fun stopVpn() {
        startService(PassthroughVpnService.stopIntent(this))
    }


    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RC_REQUEST_VPN_PERMISSION = 100
    }
}