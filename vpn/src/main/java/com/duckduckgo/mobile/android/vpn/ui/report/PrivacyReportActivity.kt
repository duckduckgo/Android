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

package com.duckduckgo.mobile.android.vpn.ui.report

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import com.google.android.material.snackbar.Snackbar
import dagger.android.AndroidInjection
import dummy.VpnViewModelFactory
import dummy.ui.VpnControllerActivity
import dummy.ui.VpnDiagnosticsActivity
import nl.dionsegijn.konfetti.KonfettiView
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber
import javax.inject.Inject

class PrivacyReportActivity : AppCompatActivity(R.layout.activity_vpn_privacy_report) {

    @Inject
    lateinit var viewModelFactory: VpnViewModelFactory

    @Inject
    lateinit var trackerListProvider: TrackerListProvider

    private lateinit var reportSummaryTextView: TextView
    private lateinit var reportSummaryEnabledTooltip: TextView
    private lateinit var reportSummaryDisabledTooltip: TextView
    private lateinit var reportSummaryMoreToggle: TextView

    private lateinit var reportCardView: CardView
    private lateinit var collapsedTrackersLayout: LinearLayout
    private lateinit var expandedTrackersLayout: LinearLayout

    private lateinit var deviceShieldDisabledCard: CardView
    private lateinit var vpnRunningToggleButton: Button

    private lateinit var viewKonfetti: KonfettiView

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        bindViewReferences()
        observeViewModel()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> handleVpnPermissionResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.vpn_debug_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.debugLogging).isChecked = viewModel.getDebugLoggingPreference()
        menu.findItem(R.id.customDnsServer)?.let {
            it.isChecked = viewModel.isCustomDnsServerSet()
            it.isEnabled = !TrackerBlockingVpnService.isServiceRunning(this)
        }
        menu.findItem(R.id.blockFacebookDomains)?.let {
            it.isChecked = viewModel.getBlockFacebookDomainsPreference()
            trackerListProvider.includeFacebookDomains = it.isChecked
        }
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
                viewModel.useDebugLogging(enabled)
                reconfigureTimber(enabled)
                true
            }
            R.id.customDnsServer -> {
                val enabled = !item.isChecked
                viewModel.useCustomDnsServer(enabled)
                true
            }
            R.id.blockFacebookDomains -> {
                val enabled = !item.isChecked
                viewModel.blockFacebookDomains(enabled)
                trackerListProvider.includeFacebookDomains = enabled
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    private fun launchFeedback() {
        startActivity(Intent(Intent.ACTION_VIEW, VpnControllerActivity.FEEDBACK_URL))
    }

    private fun observeViewModel() {
        viewModel.getReport().observe(this) {
            renderTrackersBlocked(it.totalCompanies, it.trackerList)
        }
        viewModel.vpnRunning.observe(this) {
            renderVpnEnabledState(it)
        }
        lifecycle.addObserver(viewModel)
    }

    private fun handleVpnPermissionResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_CANCELED -> {
                Timber.i("User cancelled and refused VPN permission")
            }
            Activity.RESULT_OK -> {
                Timber.i("User granted VPN permission")
                startVpn()
            }
        }
    }

    private fun bindViewReferences() {
        reportCardView = findViewById(R.id.deviceShieldReportEntries)
        reportSummaryTextView = findViewById(R.id.privacyReportTrackersBlocked)
        reportSummaryEnabledTooltip = findViewById(R.id.privacyReportEnabledTooltip)
        reportSummaryDisabledTooltip = findViewById(R.id.privacyReportDisabledTooltip)
        reportSummaryMoreToggle = findViewById(R.id.privacyReportToggle)
        deviceShieldDisabledCard = findViewById(R.id.deviceShieldDisabledCardView)
        vpnRunningToggleButton = findViewById(R.id.privacyReportToggleButton)
        collapsedTrackersLayout = findViewById(R.id.deviceShieldCollapsedTrackers)
        expandedTrackersLayout = findViewById(R.id.deviceShieldExpandedTrackers)
        viewKonfetti = findViewById(R.id.deviceShieldKonfetti)

        vpnRunningToggleButton.setOnClickListener {
            startVpnIfAllowed()
        }
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> startVpn()
            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
        }
    }

    private fun startVpn() {
        startService(TrackerBlockingVpnService.startIntent(this))

        renderVpnEnabledState(true)
        Snackbar.make(vpnRunningToggleButton, R.string.deviceShieldEnabledSnackbar, Snackbar.LENGTH_LONG).show()
        launchKonfetti()
    }

    private fun launchKonfetti() {

        val magenta = ResourcesCompat.getColor(getResources(), R.color.magenta, null)
        val blue = ResourcesCompat.getColor(getResources(), R.color.accentBlue, null)
        val purple = ResourcesCompat.getColor(getResources(), R.color.purple, null)
        val green = ResourcesCompat.getColor(getResources(), R.color.green, null)
        val yellow = ResourcesCompat.getColor(getResources(), R.color.yellow, null)

        viewKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(1f, 5f)
            .setFadeOutEnabled(true)
            .setTimeToLive(2000L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(-50f, viewKonfetti.width + 50f, -50f, -50f)
            .streamFor(50, 4000L)
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

    private fun renderTrackersBlocked(totalCompanies: Int, trackers: List<VpnTrackerAndCompany>) {
        if (trackers.isEmpty()) {
            reportSummaryTextView.text = getString(R.string.deviceShieldEnabledTooltip)
            reportSummaryMoreToggle.isVisible = false
        } else {
            reportSummaryDisabledTooltip.isVisible = false
            val totalCompanies = resources.getQuantityString(R.plurals.privacyReportCompaniesBlocked, totalCompanies, totalCompanies)
            reportSummaryTextView.text = resources.getQuantityString(R.plurals.privacyReportTrackersBlocked, trackers.size, trackers.size, totalCompanies)

            renderTrackerCompanies(trackers)
        }
    }

    private fun renderTrackerCompanies(trackers: List<VpnTrackerAndCompany>) {

        collapsedTrackersLayout.removeAllViews()
        expandedTrackersLayout.removeAllViews()

        val trackersToRender = trackers.take(MAX_TRACKERS)

        if (trackersToRender.size > TRACKERS_COLLAPSED) {
            reportSummaryMoreToggle.isVisible = true
            val firstFive = trackersToRender.subList(0, TRACKERS_COLLAPSED)
            val rest = trackersToRender.subList(TRACKERS_COLLAPSED, trackersToRender.size)
            firstFive.forEach {
                val trackerView = buildTrackerView(it)
                collapsedTrackersLayout.addView(trackerView)
            }
            rest.forEach {
                val trackerView = buildTrackerView(it)
                expandedTrackersLayout.addView(trackerView)
            }
            var expanded = false
            reportSummaryMoreToggle.setOnClickListener {
                expanded = !expanded
                if (expanded) {
                    expandedTrackersLayout.visibility = View.VISIBLE
                    reportSummaryMoreToggle.text = getString(R.string.privacyReportToggleLess)
                } else {
                    expandedTrackersLayout.visibility = View.GONE
                    reportSummaryMoreToggle.text = getString(R.string.privacyReportToggleMore)
                }
            }

        } else {
            reportSummaryMoreToggle.isVisible = false
            trackersToRender.forEach {
                val trackerView = buildTrackerView(it)
                collapsedTrackersLayout.addView(trackerView)
            }
        }
    }

    private fun buildTrackerView(trackerAndCompany: VpnTrackerAndCompany): View {
        val inflater = LayoutInflater.from(this)
        val inflatedView = inflater.inflate(R.layout.view_privacy_report_tracker_entry, null)

        val companyTextView = inflatedView.findViewById<TextView>(R.id.privacyReportTrackerEntry)
        val timeTextView = inflatedView.findViewById<TextView>(R.id.privacyReportTrackerTimeAgo)
        val companyImageView = inflatedView.findViewById<ImageView>(R.id.privacyReportTrackerCompany)

        val companyImage = when (trackerAndCompany.trackerCompany.company) {
            "Google" -> R.drawable.network_logo_google_llc
            "Amazon" -> R.drawable.network_logo_amazon_technologies_inc
            else -> R.drawable.network_logo_facebook_inc
        }
        companyImageView.setImageResource(companyImage)

        val timestamp = LocalDateTime.parse(trackerAndCompany.tracker.timestamp)
        val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
        val timeRunning = TimePassed.fromMilliseconds(timeDifference)
        timeTextView.text = getString(R.string.privacyReportAppTrackerTime, timeRunning.shortFormat())
        companyTextView.text = trackerAndCompany.trackerCompany.company

        return inflatedView
    }

    private fun renderVpnEnabledState(running: Boolean) {
        reportSummaryEnabledTooltip.isVisible = running
        reportSummaryDisabledTooltip.isVisible = !running
        deviceShieldDisabledCard.isVisible = !running
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {

        private const val RC_REQUEST_VPN_PERMISSION = 100

        private const val TRACKERS_COLLAPSED = 5
        private const val MAX_TRACKERS = 15

        fun intent(context: Context): Intent {
            return Intent(context, PrivacyReportActivity::class.java)
        }
    }
}
