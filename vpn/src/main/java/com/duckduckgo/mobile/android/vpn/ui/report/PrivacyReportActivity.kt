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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.onboarding.DeviceShieldOnboarding
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

    @Inject
    lateinit var deviceShieldOnboarding: DeviceShieldOnboarding

    private lateinit var reportSummaryTextView: TextView
    private lateinit var reportSummaryEnabledTooltip: TextView
    private lateinit var reportSummaryLink: TextView

    private lateinit var reportCardView: CardView
    private lateinit var trackersLayout: LinearLayout

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
            REQUEST_DEVICE_SHIELD_ONBOARDING -> handleDeviceShieldOnboarding(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val celebrate = intent.getBooleanExtra(CELEBRATION_EXTRA, false)

        if (celebrate) celebrate()
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
            trackerListProvider.setIncludeFacebookDomains(it.isChecked)
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
                trackerListProvider.setIncludeFacebookDomains(enabled)
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
            renderTrackersBlocked(it.totalCompanies, it.totalTrackers, it.companiesBlocked)
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

    private fun handleDeviceShieldOnboarding(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                Timber.i("User enabled VPN during onboarding")
                launchKonfetti()
            }
            else -> {
                Timber.i("User cancelled onboarding and refused VPN permission")
            }
        }
    }

    private fun bindViewReferences() {
        reportCardView = findViewById(R.id.deviceShieldReportEntries)
        reportSummaryTextView = findViewById(R.id.privacyReportSummary)
        reportSummaryEnabledTooltip = findViewById(R.id.privacyReportEnabledTooltip)
        reportSummaryLink = findViewById(R.id.privacyReportHyperlink)
        deviceShieldDisabledCard = findViewById(R.id.deviceShieldDisabledCardView)
        vpnRunningToggleButton = findViewById(R.id.privacyReportToggleButton)
        trackersLayout = findViewById(R.id.deviceShieldTrackers)
        viewKonfetti = findViewById(R.id.deviceShieldKonfetti)

        reportSummaryLink.setOnClickListener {

            Intent().apply {
                val url = "https://spreadprivacy.com/followed-by-ads/"
                component = ComponentName(applicationContext.packageName, "com.duckduckgo.app.browser.BrowserActivity")
                putExtra(Intent.EXTRA_TEXT, url)
            }.also { startActivity(it) }

            finish()
        }
        vpnRunningToggleButton.setOnClickListener {
            enableDeviceShield()
        }
    }

    @Suppress("DEPRECATION")
    private fun enableDeviceShield() {
        val deviceShieldOnboardingIntent = deviceShieldOnboarding.prepare(this)
        if (deviceShieldOnboardingIntent == null) {
            startVpnIfAllowed()
        } else {
            startActivityForResult(deviceShieldOnboardingIntent, REQUEST_DEVICE_SHIELD_ONBOARDING)
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
        celebrate()
    }

    private fun celebrate() {
        Snackbar.make(vpnRunningToggleButton, R.string.deviceShieldEnabledSnackbar, Snackbar.LENGTH_LONG).show()
        launchKonfetti()
    }

    private fun launchKonfetti() {

        val magenta = ResourcesCompat.getColor(getResources(), R.color.magenta, null)
        val blue = ResourcesCompat.getColor(getResources(), R.color.accentBlue, null)
        val purple = ResourcesCompat.getColor(getResources(), R.color.purple, null)
        val green = ResourcesCompat.getColor(getResources(), R.color.green, null)
        val yellow = ResourcesCompat.getColor(getResources(), R.color.yellow, null)

        val displayWidth = resources.displayMetrics.widthPixels

        viewKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(1f, 5f)
            .setFadeOutEnabled(true)
            .setTimeToLive(2000L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
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

    private fun renderTrackersBlocked(
        totalCompanies: Int,
        trackersSize: Int,
        companiesBlocked: List<PrivacyReportViewModel.PrivacyReportView.CompanyTrackers>
    ) {
        if (companiesBlocked.isNotEmpty()) {
            reportSummaryLink.isVisible = true
            val totalCompanies = resources.getQuantityString(R.plurals.privacyReportCompaniesBlocked, totalCompanies, totalCompanies)
            reportSummaryTextView.text = resources.getQuantityString(R.plurals.privacyReportTrackersBlocked, trackersSize, trackersSize, totalCompanies)
            renderTrackerCompanies(companiesBlocked)
        }
    }

    private fun renderTrackerCompanies(trackers: List<PrivacyReportViewModel.PrivacyReportView.CompanyTrackers>) {
        trackersLayout.removeAllViews()
        trackers.forEach {
            val trackerView = buildTrackerView(it)
            trackersLayout.addView(trackerView)
        }
        trackersLayout.isVisible = true
    }

    private fun buildTrackerView(trackerAndCompany: PrivacyReportViewModel.PrivacyReportView.CompanyTrackers): View {
        val inflater = LayoutInflater.from(this)
        val inflatedView = inflater.inflate(R.layout.view_privacy_report_tracker_entry, null)

        val companyTextView = inflatedView.findViewById<TextView>(R.id.privacyReportTrackerEntry)
        val companyTimesBlockedTextView = inflatedView.findViewById<TextView>(R.id.privacyReportTrackerCompanyBlockedTimesLabel)
        val timeTextView = inflatedView.findViewById<TextView>(R.id.privacyReportTrackerTimeAgo)
        val companyImageView = inflatedView.findViewById<ImageView>(R.id.privacyReportTrackerCompany)

        val companyImage = when (trackerAndCompany.companyName) {
            "Google" -> R.drawable.network_logo_google_llc
            "Amazon" -> R.drawable.network_logo_amazon_technologies_inc
            else -> R.drawable.network_logo_facebook_inc
        }
        companyImageView.setImageResource(companyImage)

        companyTextView.text = trackerAndCompany.companyName
        companyTimesBlockedTextView.text = resources.getQuantityString(
            R.plurals.privacyReportCompanyTimesBlocked,
            trackerAndCompany.totalTrackers,
            trackerAndCompany.totalTrackers
        )

        val timestamp = LocalDateTime.parse(trackerAndCompany.lastTracker.tracker.timestamp)
        val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
        val timeRunning = TimePassed.fromMilliseconds(timeDifference)
        timeTextView.text = getString(R.string.privacyReportAppTrackerTime, timeRunning.shortFormat()).capitalize()

        return inflatedView
    }

    private fun renderVpnEnabledState(running: Boolean) {
        reportSummaryEnabledTooltip.isVisible = running
        deviceShieldDisabledCard.isVisible = !running
        if (!trackersLayout.isVisible) {
            if (running) {
                reportSummaryTextView.text = getString(R.string.deviceShieldEnabledTooltip)
                reportSummaryLink.isVisible = true
            } else {
                reportSummaryLink.isVisible = false
                reportSummaryTextView.text = getString(R.string.deviceShieldDisabledTooltip)
            }
        }

    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {

        private const val RC_REQUEST_VPN_PERMISSION = 100
        private const val REQUEST_DEVICE_SHIELD_ONBOARDING = 101

        private const val CELEBRATION_EXTRA = "CELEBRATION_EXTRA"

        fun intent(context: Context, celebrate: Boolean = false): Intent {
            return Intent(context, PrivacyReportActivity::class.java).apply {
                putExtra(CELEBRATION_EXTRA, celebrate)
            }
        }
    }
}
