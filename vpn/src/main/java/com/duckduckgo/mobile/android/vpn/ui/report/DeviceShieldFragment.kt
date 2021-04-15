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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboarding
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dummy.VpnViewModelFactory
import nl.dionsegijn.konfetti.KonfettiView
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber
import javax.inject.Inject

class DeviceShieldFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: VpnViewModelFactory

    @Inject
    lateinit var deviceShieldOnboarding: DeviceShieldOnboarding

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var deviceShieldCtaHeaderTextView: TextView
    private lateinit var deviceShieldCtaSubHeaderTextView: TextView
    private lateinit var deviceShieldInfoLayout: View

    private lateinit var deviceShieldDisabledLayout: View
    private lateinit var deviceShieldEnableCTA: Button

    private lateinit var viewKonfetti: KonfettiView

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_device_shield_cta, container, false)
        configureViewReferences(view)
        bindListeners(view)
        return view
    }

    private fun configureViewReferences(view: View) {
        deviceShieldCtaHeaderTextView = view.findViewById(R.id.deviceShieldCtaHeader)
        deviceShieldCtaSubHeaderTextView = view.findViewById(R.id.deviceShieldCtaSubheader)
        deviceShieldInfoLayout = view.findViewById(R.id.deviceShieldInfoLayout)
        deviceShieldEnableCTA = view.findViewById(R.id.privacyReportToggleButton)
        deviceShieldDisabledLayout = view.findViewById(R.id.deviceShieldDisabledCardView)

        viewKonfetti = view.findViewById(R.id.deviceShieldKonfetti)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onResume() {
        super.onResume()
        // This logic is to ensure the we send the analytics exactly when the fragment is visible to the user
        // There are couple nuances/issues
        // 1. multiple tabs will have this fragment attached and resumed but only one will be shown to the user
        // 2. in BrowserFragment seems to call fragment_device_shield_container.show()/hide() several times causing
        //  this fragment to be resumed and visible for a split-second when Dax is shown (aka empty tab)
        Handler(Looper.getMainLooper()).postDelayed(200) {
            if (view?.isShown == true) {
                deviceShieldPixels.didShowNewTabSummary()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_REQUEST_VPN_PERMISSION -> handleVpnPermissionResult(resultCode)
            REQUEST_DEVICE_SHIELD_ONBOARDING -> handleDeviceShieldOnboarding(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
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
                startActivity(PrivacyReportActivity.intent(requireActivity(), celebrate = true))
            }
            else -> {
                Timber.i("User cancelled onboarding and refused VPN permission")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.vpnRunning.observe(this) {
            renderVpnEnabledState(it)
        }
        viewModel.getReport().observe(this) {
            renderTrackersBlocked(it.totalTrackers, it.companiesBlocked)
        }
        lifecycle.addObserver(viewModel)
    }

    private fun bindListeners(view: View) {
        deviceShieldInfoLayout.setOnClickListener {
            startActivity(PrivacyReportActivity.intent(requireActivity())).also {
                deviceShieldPixels.didPressNewTabSummary()
            }
        }
        deviceShieldEnableCTA.setOnClickListener {
            deviceShieldPixels.enableFromNewTab()
            enableDeviceShield()
        }
    }

    @Suppress("DEPRECATION")
    private fun enableDeviceShield() {
        val deviceShieldOnboardingIntent = deviceShieldOnboarding.prepare(requireActivity())
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

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(requireActivity())
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, RC_REQUEST_VPN_PERMISSION)
    }

    private fun startVpn() {
        Snackbar.make(deviceShieldInfoLayout, R.string.deviceShieldEnabledSnackbar, Snackbar.LENGTH_SHORT).show()
        requireActivity().startService(TrackerBlockingVpnService.startIntent(requireActivity()))
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

    private fun renderTrackersBlocked(totalTrackers: Int, companiesBlocked: List<PrivacyReportViewModel.PrivacyReportView.CompanyTrackers>) {
        if (companiesBlocked.isEmpty()) {
            deviceShieldCtaHeaderTextView.text = getString(R.string.deviceShieldEnabledTooltip)
            deviceShieldCtaSubHeaderTextView.isVisible = false
        } else {
            deviceShieldCtaHeaderTextView.text = resources.getQuantityString(R.plurals.deviceShieldCtaTrackersBlocked, totalTrackers, totalTrackers)
            val lastTracker = companiesBlocked.first().lastTracker
            val timestamp = LocalDateTime.parse(lastTracker.timestamp)
            val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            deviceShieldCtaSubHeaderTextView.isVisible = true
            deviceShieldCtaSubHeaderTextView.text =
                getString(R.string.deviceShieldLastTrackerBlocked, lastTracker.company, timeRunning.shortFormat())
        }
    }

    private fun renderVpnEnabledState(running: Boolean) {
        deviceShieldDisabledLayout.isVisible = !running

        if (!deviceShieldCtaSubHeaderTextView.isVisible) {
            if (running) {
                deviceShieldCtaHeaderTextView.text = getString(R.string.deviceShieldEnabledTooltip)
            } else {
                deviceShieldCtaHeaderTextView.text = getString(R.string.deviceShieldDisabledHeader)
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

    }

}
