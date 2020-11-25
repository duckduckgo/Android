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
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.DataSizeFormatter
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.model.dateOfPreviousMidnight
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataTransfer
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import dummy.VpnViewModelFactory
import dummy.quietlySetIsChecked
import dummy.ui.VpnControllerViewModel.TrackersBlocked
import kotlinx.coroutines.*
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
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

    private var lastTrackerBlocked: VpnTrackerAndCompany? = null
    private var timerUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))

        AndroidInjection.inject(this)

        setViewReferences()
        configureUiHandlers()

        subscribeForViewUpdates()
    }

    private fun subscribeForViewUpdates() {
        val midnight = dateOfPreviousMidnight()

        viewModel.getRunningTimeUpdates(midnight).observe(this) {
            renderTimeRunning(it.runningTimeMillis)
            renderVpnEnabledState(it.isRunning)
        }

        viewModel.getDataTransferredUpdates(midnight).observe(this) {
            renderDataStats(dataSent = it.sent, dataReceived = it.received)
        }

        viewModel.getTrackerBlockedUpdates(midnight).observe(this) {
            renderTrackerData(it)
        }

        viewModel.getVpnState().observe(this) {
            renderUuid(it.uuid)
        }
    }

    override fun onStart() {
        super.onStart()

        timerUpdateJob?.cancel()
        timerUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateRelativeTimes()
                delay(1_000)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        timerUpdateJob?.cancel()
    }

    private fun updateRelativeTimes() {
        lastTrackerDomainTextView.text = generateLastTrackerBlocked(lastTrackerBlocked)
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
        uuidTextView.setOnClickListener {
            val manager: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData = ClipData.newPlainText("VPN UUID", uuidTextView.text)
            manager.setPrimaryClip(clipData)
            Snackbar.make(uuidTextView, "UUID is now copied to the Clipboard", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vpn_controller_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderTimeRunning(timeRunningMillis: Long) {
        timeRunningTodayTextView.text = generateTimeRunningMessage(timeRunningMillis)
    }

    private fun renderDataStats(dataSent: DataTransfer, dataReceived: DataTransfer) {
        dataSentTextView.text = getString(
            R.string.vpnDataTransferred,
            dataSizeFormatter.format(dataSent.dataSize),
            packetsFormatter.format(dataSent.numberPackets)
        )
        dataReceivedTextView.text = getString(
            R.string.vpnDataTransferred,
            dataSizeFormatter.format(dataReceived.dataSize),
            packetsFormatter.format(dataReceived.numberPackets)
        )
    }

    private fun renderVpnEnabledState(running: Boolean) {
        vpnRunningToggleButton.quietlySetIsChecked(running, runningButtonChangeListener)
    }

    private fun renderUuid(uuid: String) {
        uuidTextView.text = uuid
    }

    private fun renderTrackerData(trackerData: TrackersBlocked) {
        trackerCompaniesBlockedTextView.text = generateTrackerCompaniesBlocked(trackerData.byCompany().size)
        trackersBlockedTextView.text = generateTrackersBlocked(trackerData.trackerList.size)
        lastTrackerBlocked = trackerData.trackerList.firstOrNull()
        updateRelativeTimes()
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> startVpn()
            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
        }
    }

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> handleVpnPermissionResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_CANCELED -> {
                Timber.i("User cancelled and refused VPN permission")
                vpnRunningToggleButton.quietlySetIsChecked(false, runningButtonChangeListener)
            }
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

    private fun generateTimeRunningMessage(timeRunningMillis: Long): String {
        return if (timeRunningMillis == 0L) {
            getString(R.string.vpnNotRunYet)
        } else {
            return getString(R.string.vpnTimeRunning, TimePassed.fromMilliseconds(timeRunningMillis).format())
        }
    }

    private fun generateLastTrackerBlocked(lastTracker: VpnTrackerAndCompany?): String {
        if (lastTracker == null) return ""

        val timestamp = LocalDateTime.parse(lastTracker.tracker.timestamp)
        val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
        val timeRunning = TimePassed.fromMilliseconds(timeDifference)
        return "Latest tracker blocked ${timeRunning.format()} ago\n${lastTracker.tracker.domain}\n(owned by ${lastTracker.trackerCompany.company})"
    }

    private fun generateTrackerCompaniesBlocked(totalTrackerCompanies: Int): String {
        return if (totalTrackerCompanies == 0) {
            applicationContext.getString(R.string.vpnTrackerCompaniesNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackerCompaniesBlocked, totalTrackerCompanies)
        }
    }

    private fun generateTrackersBlocked(totalTrackers: Int): String {
        return if (totalTrackers == 0) {
            applicationContext.getString(R.string.vpnTrackersNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackersBlocked, totalTrackers)
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
