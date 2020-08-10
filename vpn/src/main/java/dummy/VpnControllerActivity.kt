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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.vpn.service.PassthroughVpnService
import com.duckduckgo.mobile.android.vpn.R
import kotlinx.coroutines.*
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer

class VpnControllerActivity : AppCompatActivity(R.layout.activity_vpn_controller), CoroutineScope by MainScope() {

    private lateinit var vpnRunningToggleButton: ToggleButton
    private lateinit var vpnPermissionTextView: TextView
    private lateinit var processorStartButton: Button
    private lateinit var processorStopButton: Button
    private lateinit var addPacketButton: Button

    private var vpnService: PassthroughVpnService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))
        bindService(PassthroughVpnService.serviceIntent(this), serviceConnection, Context.BIND_AUTO_CREATE)
        setViewReferences()
        configureUiHandlers()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }

    private fun setViewReferences() {
        vpnRunningToggleButton = findViewById(R.id.vpnRunningButton)
        vpnPermissionTextView = findViewById(R.id.vpnPermissionStatus)
        processorStartButton = findViewById(R.id.processStart)
        processorStopButton = findViewById(R.id.processStop)
        addPacketButton = findViewById(R.id.addPacket)
    }

    private fun configureUiHandlers() {
        vpnRunningToggleButton.setOnCheckedChangeListener(runningButtonChangeListener)
        processorStartButton.setOnClickListener {
            if (vpnService == null) {
                Toast.makeText(this, "VPN Service not bound yet", Toast.LENGTH_SHORT).show()
            } else {
                vpnService?.udpPacketProcessor?.start()
                vpnService?.tcpPacketProcessor?.start()
            }
        }
        processorStopButton.setOnClickListener {
            vpnService?.udpPacketProcessor?.stop()
            vpnService?.tcpPacketProcessor?.stop()
        }
        addPacketButton.setOnClickListener {
            vpnService?.queues?.udpDeviceToNetwork?.offer(Packet(ByteBuffer.allocate(Short.MAX_VALUE.toInt())))
        }
        addPacketButton.setOnLongClickListener {
            vpnService?.queues?.tcpDeviceToNetwork?.offer(Packet(ByteBuffer.allocate(Short.MAX_VALUE.toInt())))
            true
        }
        //addPacketButton.setOnLongClickListener { bulkAddPackets(); true }

    }

//    private fun bulkAddPackets() {
//        for (i in 0 until 100) {
//            vpnService?.deviceToNetworkPacketProcessor?.addPacket(Packet(ByteBuffer.allocate(0)))
//        }
//    }

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

    private val serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            vpnService = (binder as PassthroughVpnService.VpnServiceBinder).getService()
            Timber.i("Bound to VPN service")
            updateRunningToggleButtonState()
        }

        override fun onServiceDisconnected(component: ComponentName) {
            vpnService = null
            Timber.i("Unbound from VPN service")
        }
    }

    private val runningButtonChangeListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Timber.i("Toggle changed. enabled=$checked")

        if (checked) {
            startVpnIfAllowed()
        } else {
            stopVpn()
        }
    }

    private fun updateRunningToggleButtonState() {
        vpnRunningToggleButton.quietlySetIsChecked(PassthroughVpnService.running, runningButtonChangeListener)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RC_REQUEST_VPN_PERMISSION = 100
    }
}