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

package dummy.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import dummy.quietlySetIsChecked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber
import javax.inject.Inject

class VpnControllerActivity : AppCompatActivity(R.layout.activity_vpn_controller), CoroutineScope by MainScope() {

    private lateinit var lastTrackerDomainTextView: TextView
    private lateinit var timeRunningTodayTextView: TextView
    private lateinit var trackersBlockedTextView: TextView
    private lateinit var dataSentTextView: TextView
    private lateinit var dataReceivedTextView: TextView
    private lateinit var vpnRunningToggleButton: ToggleButton
    private lateinit var uuidTextView: TextView

    @Inject
    lateinit var appTrackerBlockerStatsRepository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    private lateinit var viewModel: VpnControllerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))

        AndroidInjection.inject(this)

        setViewReferences()
        configureUiHandlers()

        viewModel = VpnControllerViewModel(appTrackerBlockerStatsRepository)
        viewModel.viewState.observe(this, Observer {
            it?.let { render(it) }
        })
        viewModel.onCreate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }

    private fun setViewReferences() {
        trackersBlockedTextView = findViewById(R.id.vpnTrackersBlocked)
        lastTrackerDomainTextView = findViewById(R.id.vpnLastTrackerDomain)
        timeRunningTodayTextView = findViewById(R.id.vpnTodayRunningTime)
        dataSentTextView = findViewById(R.id.vpnSentStats)
        dataReceivedTextView = findViewById(R.id.vpnReceivedStats)
        vpnRunningToggleButton = findViewById(R.id.vpnRunningButton)
        uuidTextView = findViewById(R.id.vpnUUID)
    }

    private fun configureUiHandlers() {
        vpnRunningToggleButton.setOnCheckedChangeListener(runningButtonChangeListener)
    }

    private fun render(viewState: VpnControllerViewModel.ViewState) {
        uuidTextView.text = viewState.uuid
        uuidTextView.setOnClickListener {
            val manager: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData = ClipData.newPlainText("VPN UUID", viewState.uuid)
            manager.setPrimaryClip(clipData)
            Snackbar.make(uuidTextView, "UUID is now copied to the Clipboard", Snackbar.LENGTH_SHORT).show()
        }
        trackersBlockedTextView.text = viewState.trackersBlocked
        lastTrackerDomainTextView.text = viewState.lastTrackerBlocked
        vpnRunningToggleButton.quietlySetIsChecked(viewState.isVpnRunning, runningButtonChangeListener)
        timeRunningTodayTextView.text = generateTimeRunningMessage(viewState)
        dataSentTextView.text = viewState.dataSent
        dataReceivedTextView.text = viewState.dataReceived
    }

    private fun generateTimeRunningMessage(viewState: VpnControllerViewModel.ViewState): String {
        return if (viewState.connectionStats == null) {
            "VPN hasn't been running yet"
        } else {
            if (viewState.isVpnRunning) {
                if (viewState.connectionStats.timeRunning == 0L) {
                    // first time running the vpn in this block, time running = time last updated - now()
                    val timeDifference = viewState.connectionStats.lastUpdated.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
                    val timeRunning = TimePassed.fromMilliseconds(timeDifference)
                    "Today, the VPN has been running for $timeRunning"
                } else {
                    val timePassed = TimePassed.fromMilliseconds(viewState.connectionStats.timeRunning)
                    "Today, the VPN has been running for $timePassed"
                }
            } else {
                val timePassed = TimePassed.fromMilliseconds(viewState.connectionStats.timeRunning)
                "Today, the VPN ran for $timePassed"
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
        startService(TrackerBlockingVpnService.startIntent(this))
    }

    private fun stopVpn() {
        startService(TrackerBlockingVpnService.stopIntent(this))
    }

    private val runningButtonChangeListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Timber.i("Toggle changed. enabled=$checked")
        if (checked) {
            startVpnIfAllowed()
        } else {
            stopVpn()
        }
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnControllerActivity::class.java)
        }

        private const val RC_REQUEST_VPN_PERMISSION = 100
    }
}
