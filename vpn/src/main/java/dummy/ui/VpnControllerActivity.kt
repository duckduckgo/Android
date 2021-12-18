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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataTransfer
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.AndroidInjection
import dummy.ui.VpnControllerViewModel.AppTrackersBlocked
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.*
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber

class VpnControllerActivity : DuckDuckGoActivity(), CoroutineScope by MainScope() {

    private lateinit var lastAppTrackerDomainTextView: TextView
    private lateinit var appTrackerCompaniesBlockedTodayTextView: TextView
    private lateinit var appTrackersBlockedTodayTextView: TextView
    private lateinit var appTrackerCompaniesBlockedWeekTextView: TextView
    private lateinit var appTrackersBlockedWeekTextView: TextView

    private lateinit var lastWebTrackerDomainTextView: TextView
    private lateinit var webTrackerCompaniesBlockedTodayTextView: TextView
    private lateinit var webTrackersBlockedTodayTextView: TextView
    private lateinit var webTrackerCompaniesBlockedWeekTextView: TextView
    private lateinit var webTrackersBlockedWeekTextView: TextView

    private lateinit var appVersionText: TextView
    private lateinit var timeRunningTodayTextView: TextView
    private lateinit var dataSentTextView: TextView
    private lateinit var dataReceivedTextView: TextView
    private lateinit var uuidTextView: TextView

    @Inject lateinit var vpnDatabase: VpnDatabase

    @Inject lateinit var dataSizeFormatter: DataSizeFormatter

    private val viewModel: VpnControllerViewModel by bindViewModel()
    private val packetsFormatter = NumberFormat.getInstance()

    private var lastAppTrackerBlocked: VpnTracker? = null
    private var lastWebTrackerBlocked: WebTrackerBlocked? = null

    private var timerUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vpn_controller)
        setupToolbar(findViewById(R.id.toolbar))

        AndroidInjection.inject(this)

        setViewReferences()
        configureUiHandlers()

        subscribeForViewUpdates()

        reconfigureTimber(viewModel.getDebugLoggingPreference())
    }

    private fun subscribeForViewUpdates() {

        viewModel.getRunningTimeUpdates { dateOfPreviousMidnight() }.observe(this) {
            renderTimeRunning(it.runningTimeMillis)
        }

        viewModel.getDataTransferredUpdates { dateOfPreviousMidnight() }.observe(this) {
            renderDataStats(dataSent = it.sent, dataReceived = it.received)
        }

        viewModel.getAppTrackerBlockedUpdates { dateOfPreviousMidnight() }.observe(this) {
            renderTodayAppTrackerData(it)
        }

        viewModel.getAppTrackerBlockedUpdates { dateOfLastWeek() }.observe(this) {
            renderLastWeekAppTrackerData(it)
        }

        viewModel.getWebTrackerBlockedUpdates { dateOfPreviousMidnight() }.observe(this) {
            renderTodayWebTrackerData(it)
        }

        viewModel.getWebTrackerBlockedUpdates { dateOfLastWeek() }.observe(this) {
            renderLastWeekWebTrackerData(it)
        }

        viewModel.getVpnState().observe(this) { renderUuid(it?.uuid ?: "initializing") }
    }

    override fun onStart() {
        super.onStart()

        timerUpdateJob?.cancel()
        timerUpdateJob =
            lifecycleScope.launch {
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
        lastAppTrackerDomainTextView.text = generateLastAppTrackerBlocked(lastAppTrackerBlocked)
    }

    private fun setViewReferences() {
        appTrackerCompaniesBlockedTodayTextView =
            findViewById(R.id.vpnAppTrackerCompaniesBlockedToday)
        appTrackersBlockedTodayTextView = findViewById(R.id.vpnAppTrackersBlockedToday)
        appTrackerCompaniesBlockedWeekTextView =
            findViewById(R.id.vpnAppTrackerCompaniesBlockedWeek)
        appTrackersBlockedWeekTextView = findViewById(R.id.vpnAppTrackersBlockedWeek)
        lastAppTrackerDomainTextView = findViewById(R.id.vpnAppLastTrackerDomain)

        webTrackerCompaniesBlockedTodayTextView =
            findViewById(R.id.vpnWebTrackerCompaniesBlockedToday)
        webTrackersBlockedTodayTextView = findViewById(R.id.vpnWebTrackersBlockedToday)
        webTrackerCompaniesBlockedWeekTextView =
            findViewById(R.id.vpnWebTrackersCompaniesBlockedWeek)
        webTrackersBlockedWeekTextView = findViewById(R.id.vpnWebTrackersBlockedWeek)
        lastWebTrackerDomainTextView = findViewById(R.id.vpnAppLastTrackerDomain)

        timeRunningTodayTextView = findViewById(R.id.vpnTodayRunningTime)
        dataSentTextView = findViewById(R.id.vpnSentStats)
        dataReceivedTextView = findViewById(R.id.vpnReceivedStats)
        uuidTextView = findViewById(R.id.vpnUUID)
        appVersionText =
            findViewById<TextView>(R.id.appVersionText).also {
                it.text = packageManager.getPackageInfo(packageName, 0).versionName
            }
    }

    private fun configureUiHandlers() {
        uuidTextView.setOnClickListener {
            val manager: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData = ClipData.newPlainText("VPN UUID", uuidTextView.text)
            manager.setPrimaryClip(clipData)
            Snackbar.make(
                    uuidTextView, "UUID is now copied to the Clipboard", Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun reconfigureTimber(debugLoggingEnabled: Boolean) {
        if (debugLoggingEnabled) {
            Timber.uprootAll()
            Timber.plant(Timber.DebugTree())
            Timber.w("Logging Started")
        } else {
            Timber.w("Logging Ended")
            Timber.uprootAll()
        }
    }

    private fun renderTimeRunning(timeRunningMillis: Long) {
        timeRunningTodayTextView.text = generateTimeRunningMessage(timeRunningMillis)
    }

    private fun renderDataStats(dataSent: DataTransfer, dataReceived: DataTransfer) {
        dataSentTextView.text =
            getString(
                R.string.vpnDataTransferred,
                dataSizeFormatter.format(dataSent.dataSize),
                packetsFormatter.format(dataSent.numberPackets))
        dataReceivedTextView.text =
            getString(
                R.string.vpnDataTransferred,
                dataSizeFormatter.format(dataReceived.dataSize),
                packetsFormatter.format(dataReceived.numberPackets))
    }

    private fun renderUuid(uuid: String) {
        uuidTextView.text = uuid
    }

    private fun renderTodayAppTrackerData(trackerData: AppTrackersBlocked) {
        appTrackerCompaniesBlockedTodayTextView.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = false)
        appTrackersBlockedTodayTextView.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = false)
        lastAppTrackerBlocked = trackerData.trackerList.firstOrNull()
        updateRelativeTimes()
    }

    private fun renderLastWeekAppTrackerData(trackerData: AppTrackersBlocked) {
        appTrackerCompaniesBlockedWeekTextView.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = true)
        appTrackersBlockedWeekTextView.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = true)
    }

    private fun renderTodayWebTrackerData(trackerData: VpnControllerViewModel.WebTrackersBlocked) {
        webTrackerCompaniesBlockedTodayTextView.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = false)
        webTrackersBlockedTodayTextView.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = false)
        lastWebTrackerBlocked = trackerData.trackerList.firstOrNull()
        lastWebTrackerDomainTextView.text = generateLastWebTrackerBlocked(lastWebTrackerBlocked)
    }

    private fun renderLastWeekWebTrackerData(
        trackerData: VpnControllerViewModel.WebTrackersBlocked
    ) {
        webTrackerCompaniesBlockedWeekTextView.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = true)
        webTrackersBlockedWeekTextView.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = true)
    }

    private fun generateTimeRunningMessage(timeRunningMillis: Long): String {
        return if (timeRunningMillis == 0L) {
            getString(R.string.vpnNotRunYet)
        } else {
            return getString(
                R.string.vpnTimeRunning, TimePassed.fromMilliseconds(timeRunningMillis).format())
        }
    }

    private fun generateLastAppTrackerBlocked(lastTracker: VpnTracker?): String {
        if (lastTracker == null) return ""

        val timestamp = LocalDateTime.parse(lastTracker.timestamp)
        val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
        val timeRunning = TimePassed.fromMilliseconds(timeDifference)
        return "Latest tracker blocked ${timeRunning.format()} ago\n${lastTracker.domain}\n(owned by ${lastTracker.company})"
    }

    private fun generateLastWebTrackerBlocked(lastTracker: WebTrackerBlocked?): String {
        if (lastTracker == null) return ""

        val timestamp = LocalDateTime.parse(lastTracker.timestamp)
        val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
        val timeRunning = TimePassed.fromMilliseconds(timeDifference)
        return "Latest tracker blocked ${timeRunning.format()} ago\n${lastTracker.trackerUrl}\n(owned by ${lastTracker.trackerCompany})"
    }

    private fun generateTrackerCompaniesBlocked(
        totalTrackerCompanies: Int,
        displayWeek: Boolean
    ): String {
        return if (totalTrackerCompanies == 0) {
            applicationContext.getString(R.string.vpnTrackerCompaniesNone)
        } else {
            return if (displayWeek) {
                applicationContext.getString(
                    R.string.vpnTrackerCompaniesBlockedThisWeek, totalTrackerCompanies)
            } else {
                applicationContext.getString(
                    R.string.vpnTrackerCompaniesBlockedToday, totalTrackerCompanies)
            }
        }
    }

    private fun generateTrackersBlocked(totalTrackers: Int, displayWeek: Boolean): String {
        return if (totalTrackers == 0) {
            applicationContext.getString(R.string.vpnTrackersNone)
        } else {
            return if (displayWeek) {
                applicationContext.getString(R.string.vpnTrackersBlockedThisWeek, totalTrackers)
            } else {
                applicationContext.getString(R.string.vpnTrackersBlockedToday, totalTrackers)
            }
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
        val FEEDBACK_URL = "https://form.asana.com?k=j2t0mHOc9nMVTDqg5OHPJw&d=137249556945".toUri()
    }
}

@Suppress("UNCHECKED_CAST")
@ContributesMultibinding(AppScope::class)
class VpnControllerViewModelFactory
@Inject
constructor(
    private val appTrackerBlockedRepository: AppTrackerBlockingStatsRepository,
    private val webTrackersBlockedRepository: WebTrackersBlockedRepository,
    private val applicationContext: Context,
    private val vpnPreferences: VpnPreferences
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(VpnControllerViewModel::class.java) -> {
                    return VpnControllerViewModel(
                        appTrackerBlockedRepository,
                        webTrackersBlockedRepository,
                        applicationContext,
                        vpnPreferences) as
                        T
                }
                else -> null
            }
        }
    }
}
