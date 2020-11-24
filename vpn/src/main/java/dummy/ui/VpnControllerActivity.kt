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
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.DataSizeFormatter
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import dummy.VpnViewModelFactory
import dummy.quietlySetIsChecked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import timber.log.Timber
import java.text.NumberFormat
import javax.inject.Inject

class VpnControllerActivity : AppCompatActivity(R.layout.activity_vpn_controller), CoroutineScope by MainScope() {

    private lateinit var lastTrackerDomainTextView: TextView
    private lateinit var timeRunningTodayTextView: TextView
    private lateinit var trackerCompaniesBlockedTextView: TextView
    private lateinit var trackersBlockedTextView: TextView
    private lateinit var dataSentTextView: TextView
    private lateinit var dataReceivedTextView: TextView
    private lateinit var vpnRunningToggleButton: ToggleButton
    private lateinit var uuidTextView: TextView

    @Inject
    lateinit var appTrackerBlockerStatsRepository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var viewModelFactory: VpnViewModelFactory

    @Inject
    lateinit var dataSizeFormatter: DataSizeFormatter

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }
    private val viewModel: VpnControllerViewModel by bindViewModel()
    private val packetsFormatter = NumberFormat.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))

        AndroidInjection.inject(this)

        setViewReferences()
        configureUiHandlers()

        viewModel.viewState.observe(this, {
            it?.let { render(it) }
        })
        viewModel.onCreate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    private fun setViewReferences() {
        trackerCompaniesBlockedTextView = findViewById(R.id.vpnTrackerCompaniesBlocked)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vpn_controller_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.vpnRefreshMenu -> {
                viewModel.refreshData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun render(viewState: VpnControllerViewModel.ViewState) {
        uuidTextView.text = viewState.uuid
        uuidTextView.setOnClickListener {
            val manager: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData = ClipData.newPlainText("VPN UUID", viewState.uuid)
            manager.setPrimaryClip(clipData)
            Snackbar.make(uuidTextView, "UUID is now copied to the Clipboard", Snackbar.LENGTH_SHORT).show()
        }
        trackerCompaniesBlockedTextView.text = viewState.trackerCompaniesBlocked
        trackersBlockedTextView.text = viewState.trackersBlocked
        lastTrackerDomainTextView.text = viewState.lastTrackerBlocked
        vpnRunningToggleButton.quietlySetIsChecked(viewState.isVpnRunning, runningButtonChangeListener)
        timeRunningTodayTextView.text = generateTimeRunningMessage(viewState.timeRunningMillis)
        dataSentTextView.text = getString(R.string.vpnDataTransferred, dataSizeFormatter.format(viewState.dataSent.dataSize), packetsFormatter.format(viewState.dataSent.numberPackets))
        dataReceivedTextView.text = getString(R.string.vpnDataTransferred, dataSizeFormatter.format(viewState.dataReceived.dataSize), packetsFormatter.format(viewState.dataReceived.numberPackets))
    }

    private fun generateTimeRunningMessage(timeRunningMillis: Long): String {
        return if (timeRunningMillis == 0L) {
            getString(R.string.vpnNotRunYet)
        } else {
            return getString(R.string.vpnTimeRunning, TimePassed.fromMilliseconds(timeRunningMillis).format())
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
