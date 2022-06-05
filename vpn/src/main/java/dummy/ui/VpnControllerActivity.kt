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
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.formatters.data.DataSizeFormatter
import com.duckduckgo.app.global.formatters.time.model.TimePassed
import com.duckduckgo.app.global.formatters.time.model.dateOfLastWeek
import com.duckduckgo.app.global.formatters.time.model.dateOfPreviousMidnight
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityVpnControllerBinding
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.DataTransfer
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import dummy.ui.VpnControllerViewModel.AppTrackersBlocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import java.text.NumberFormat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class VpnControllerActivity : DuckDuckGoActivity(), CoroutineScope by MainScope() {

    @Inject lateinit var vpnDatabase: VpnDatabase

    @Inject lateinit var dataSizeFormatter: DataSizeFormatter

    private val viewModel: VpnControllerViewModel by bindViewModel()
    private val packetsFormatter = NumberFormat.getInstance()
    private val binding: ActivityVpnControllerBinding by viewBinding()

    private var lastAppTrackerBlocked: VpnTracker? = null
    private var lastWebTrackerBlocked: WebTrackerBlocked? = null

    private var timerUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        AndroidInjection.inject(this)

        setAppVersion()
        configureUiHandlers()

        subscribeForViewUpdates()
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
        binding.vpnController.vpnAppLastTrackerDomain.text = generateLastAppTrackerBlocked(lastAppTrackerBlocked)
    }

    private fun setAppVersion() {
        binding.vpnController.appVersionText.text = packageManager.getPackageInfo(packageName, 0).versionName
    }

    private fun configureUiHandlers() {
        binding.vpnController.vpnUUID.setOnClickListener {
            val manager: ClipboardManager =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData: ClipData = ClipData.newPlainText("VPN UUID", binding.vpnController.vpnUUID.text)
            manager.setPrimaryClip(clipData)
            Snackbar.make(
                binding.vpnController.vpnUUID, "UUID is now copied to the Clipboard", Snackbar.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun renderTimeRunning(timeRunningMillis: Long) {
        binding.vpnController.vpnTodayRunningTime.text = generateTimeRunningMessage(timeRunningMillis)
    }

    private fun renderDataStats(
        dataSent: DataTransfer,
        dataReceived: DataTransfer
    ) {
        binding.vpnController.vpnDataSentLabel.text =
            getString(
                R.string.vpnDataTransferred,
                dataSizeFormatter.format(dataSent.dataSize),
                packetsFormatter.format(dataSent.numberPackets)
            )
        binding.vpnController.vpnReceivedStats.text =
            getString(
                R.string.vpnDataTransferred,
                dataSizeFormatter.format(dataReceived.dataSize),
                packetsFormatter.format(dataReceived.numberPackets)
            )
    }

    private fun renderUuid(uuid: String) {
        binding.vpnController.vpnUUID.text = uuid
    }

    private fun renderTodayAppTrackerData(trackerData: AppTrackersBlocked) {
        binding.vpnController.vpnAppTrackerCompaniesBlockedToday.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = false)
        binding.vpnController.vpnAppTrackersBlockedToday.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = false)
        lastAppTrackerBlocked = trackerData.trackerList.firstOrNull()
        updateRelativeTimes()
    }

    private fun renderLastWeekAppTrackerData(trackerData: AppTrackersBlocked) {
        binding.vpnController.vpnAppTrackerCompaniesBlockedWeek.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = true)
        binding.vpnController.vpnAppTrackersBlockedWeek.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = true)
    }

    private fun renderTodayWebTrackerData(trackerData: VpnControllerViewModel.WebTrackersBlocked) {
        binding.vpnController.vpnWebTrackerCompaniesBlockedToday.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = false)
        binding.vpnController.vpnWebTrackersBlockedToday.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = false)
        lastWebTrackerBlocked = trackerData.trackerList.firstOrNull()
        binding.vpnController.vpnWebLastTrackerDomain.text = generateLastWebTrackerBlocked(lastWebTrackerBlocked)
    }

    private fun renderLastWeekWebTrackerData(
        trackerData: VpnControllerViewModel.WebTrackersBlocked
    ) {
        binding.vpnController.vpnWebTrackersCompaniesBlockedWeek.text =
            generateTrackerCompaniesBlocked(trackerData.byCompany().size, displayWeek = true)
        binding.vpnController.vpnWebTrackersBlockedWeek.text =
            generateTrackersBlocked(trackerData.trackerList.size, displayWeek = true)
    }

    private fun generateTimeRunningMessage(timeRunningMillis: Long): String {
        return if (timeRunningMillis == 0L) {
            getString(R.string.vpnNotRunYet)
        } else {
            return getString(
                R.string.vpnTimeRunning, TimePassed.fromMilliseconds(timeRunningMillis).format()
            )
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
                    R.string.vpnTrackerCompaniesBlockedThisWeek, totalTrackerCompanies
                )
            } else {
                applicationContext.getString(
                    R.string.vpnTrackerCompaniesBlockedToday, totalTrackerCompanies
                )
            }
        }
    }

    private fun generateTrackersBlocked(
        totalTrackers: Int,
        displayWeek: Boolean
    ): String {
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

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, VpnControllerActivity::class.java)
        }
    }
}
