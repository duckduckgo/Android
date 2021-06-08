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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.report.PrivacyReportViewModel
import com.duckduckgo.mobile.android.vpn.ui.report.DeviceShieldAppTrackersInfo
import dagger.android.AndroidInjection
import dummy.ui.VpnControllerActivity
import dummy.ui.VpnDiagnosticsActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DeviceShieldTrackerActivity : AppCompatActivity(R.layout.activity_device_shield_activity) {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var trackerBlockedCountView: PastWeekTrackerActivityContentView
    private lateinit var trackingAppsCountView: PastWeekTrackerActivityContentView
    private lateinit var ctaShowAll: View
    private lateinit var ctaTrackerFaq: View
    private val pastWeekTrackerActivityViewModel: PastWeekTrackerActivityViewModel by bindViewModel()
    private val privacyReportViewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        trackerBlockedCountView = findViewById(R.id.trackers_blocked_count)
        trackingAppsCountView = findViewById(R.id.tracking_apps_count)
        ctaShowAll = findViewById(R.id.cta_show_all)
        ctaTrackerFaq = findViewById(R.id.cta_tracker_faq)

        ctaShowAll.setOnClickListener {
            startActivity(DeviceShieldMostRecentActivity.intent(this))
        }

        ctaTrackerFaq.setOnClickListener {
            DeviceShieldAppTrackersInfo.intent(this).also {
                deviceShieldPixels.privacyReportArticleDisplayed()
                startActivity(it)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.activity_list,
                DeviceShieldActivityFeedFragment.newInstance(
                    DeviceShieldActivityFeedFragment.ActivityFeedConfig(
                        maxRows = 6,
                        timeWindow = 5,
                        timeWindowUnits = TimeUnit.DAYS,
                        showTimeWindowHeadings = false
                    )
                ),
                null
            )
            .commitNow()

        lifecycleScope.launch {
            pastWeekTrackerActivityViewModel.getBlockedTrackersCount()
                .combine(pastWeekTrackerActivityViewModel.getTrackingAppsCount()) { trackers, apps ->
                    TrackerCountInfo(trackers, apps)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { updateCounts(it) }
        }

        deviceShieldPixels.didShowPrivacyReport()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        intent.getParcelableExtra<ResultReceiver>(RESULT_RECEIVER_EXTRA)?.let {
            it.send(ON_LAUNCHED_CALLED_SUCCESS, null)
        }
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun updateCounts(trackerCountInfo: TrackerCountInfo) {
        trackerBlockedCountView.count = trackerCountInfo.stringTrackerCount()
        trackerBlockedCountView.footer = resources.getQuantityString(R.plurals.deviceShieldActivityPastWeekTrackerCount, trackerCountInfo.trackers.value)

        trackingAppsCountView.count = trackerCountInfo.stringAppsCount()
        trackingAppsCountView.footer = resources.getQuantityString(R.plurals.deviceShieldActivityPastWeekAppCount, trackerCountInfo.apps.value)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vpn_debug_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.debugLogging).isChecked = privacyReportViewModel.getDebugLoggingPreference()
        menu.findItem(R.id.customDnsServer)?.let {
            it.isChecked = privacyReportViewModel.isCustomDnsServerSet()
            it.isEnabled = !TrackerBlockingVpnService.isServiceRunning(this)
        }
        menu.findItem(R.id.diagnosticsScreen).isVisible = BuildConfig.DEBUG
        menu.findItem(R.id.dataScreen).isVisible = BuildConfig.DEBUG
        menu.findItem(R.id.debugLogging).isVisible = BuildConfig.DEBUG
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.reportFeedback -> {
                launchFeedback(); true
            }
            R.id.dataScreen -> {
                startActivity(VpnControllerActivity.intent(this)); true
            }
            R.id.diagnosticsScreen -> {
                startActivity(VpnDiagnosticsActivity.intent(this)); true
            }
            R.id.debugLogging -> {
                val enabled = !item.isChecked
                privacyReportViewModel.useDebugLogging(enabled)
                reconfigureTimber(enabled)
                true
            }
            R.id.customDnsServer -> {
                val enabled = !item.isChecked
                privacyReportViewModel.useCustomDnsServer(enabled)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchFeedback() {
        startActivity(Intent(Intent.ACTION_VIEW, VpnControllerActivity.FEEDBACK_URL))
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

    companion object {
        private const val RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA"
        private const val ON_LAUNCHED_CALLED_SUCCESS = 0

        fun intent(context: Context, onLaunchCallback: ResultReceiver? = null): Intent {
            return Intent(context, DeviceShieldTrackerActivity::class.java).apply {
                putExtra(RESULT_RECEIVER_EXTRA, onLaunchCallback)
            }
        }
    }

    private data class TrackerCountInfo(val trackers: TrackerCount, val apps: TrackingAppCount) {
        fun stringTrackerCount(): String {
            return String.format(Locale.US, "%,d", trackers.value)
        }
        fun stringAppsCount(): String {
            return String.format(Locale.US, "%,d", apps.value)
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }
}
