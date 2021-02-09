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
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.onboarding.DeviceShieldOnboarding
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dummy.VpnViewModelFactory
import dummy.quietlySetIsChecked
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

    private lateinit var deviceShieldCtaHeaderTextView: TextView
    private lateinit var deviceShieldCtaSubHeaderTextView: TextView
    private lateinit var deviceShieldSwitch: SwitchCompat

    private lateinit var deviceShieldSwitchImage: ImageView
    private lateinit var deviceShieldSwitchHeaderTextView: TextView
    private lateinit var deviceShieldSwitchSubHeaderTextView: TextView
    private lateinit var deviceShieldSwitchLabelTextView: TextView

    private lateinit var deviceShieldInfoLayout: View
    private lateinit var deviceShieldSwitchLayout: View

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_device_shield_cta, container, false)
        deviceShieldCtaHeaderTextView = view.findViewById(R.id.deviceShieldCtaHeader)
        deviceShieldCtaSubHeaderTextView = view.findViewById(R.id.deviceShieldCtaSubheader)
        deviceShieldSwitch = view.findViewById(R.id.deviceShieldCtaSwitch)

        deviceShieldSwitchImage = view.findViewById(R.id.deviceShieldSwitchImage)
        deviceShieldSwitchHeaderTextView = view.findViewById(R.id.deviceShieldSwitchHeader)
        deviceShieldSwitchSubHeaderTextView = view.findViewById(R.id.deviceShieldSwitchSubheader)
        deviceShieldSwitchLabelTextView = view.findViewById(R.id.deviceShieldCtaSwitchLabel)

        deviceShieldInfoLayout = view.findViewById(R.id.deviceShieldCtaLayout)
        deviceShieldSwitchLayout = view.findViewById(R.id.deviceShieldSwitchLayout)

        bindListeners(view)

        return view
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
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
            renderTrackersBlocked(it.trackerList)
        }
        lifecycle.addObserver(viewModel)
    }

    private val deviceShieldSwitchListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        Timber.i("Toggle changed. enabled=$checked")
        if (checked) {
            enableDeviceShield()
        } else {
            stopVpn()
        }

        renderDeviceShieldSwitchState(checked)
    }

    private fun bindListeners(view: View) {
        deviceShieldInfoLayout.setOnClickListener {
            startActivity(PrivacyReportActivity.intent(requireActivity()))
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
        deviceShieldSwitchImage.setImageResource(R.drawable.ic_device_shield_on)
        Snackbar.make(deviceShieldSwitchImage, R.string.deviceShieldEnabledSnackbar, Snackbar.LENGTH_SHORT).show()
        requireActivity().startService(TrackerBlockingVpnService.startIntent(requireActivity()))
    }

    private fun stopVpn() {
        deviceShieldSwitchImage.setImageResource(R.drawable.ic_device_shield_off)
        requireActivity().startService(TrackerBlockingVpnService.stopIntent(requireActivity()))
    }

    private fun renderTrackersBlocked(trackers: List<VpnTrackerAndCompany>) {
        if (trackers.isEmpty()) {
            deviceShieldCtaHeaderTextView.text = getString(R.string.deviceShieldDisabledHeader)
            deviceShieldCtaSubHeaderTextView.isVisible = deviceShieldSwitch.isChecked
            deviceShieldCtaSubHeaderTextView.text = getString(R.string.deviceShieldDisabledSubheader)
        } else {
            deviceShieldCtaHeaderTextView.text = resources.getQuantityString(R.plurals.deviceShieldCtaTrackersBlocked, trackers.size, trackers.size)
            val lastTracker = trackers.first()
            val timestamp = LocalDateTime.parse(lastTracker.tracker.timestamp)
            val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            deviceShieldCtaSubHeaderTextView.isVisible = true
            deviceShieldCtaSubHeaderTextView.text =
                getString(R.string.deviceShieldLastTrackerBlocked, lastTracker.trackerCompany.company, timeRunning.shortFormat())
        }
    }

    private fun renderVpnEnabledState(running: Boolean) {
        renderDeviceShieldSwitchState(running)
        deviceShieldSwitch.quietlySetIsChecked(running, deviceShieldSwitchListener)
    }

    private fun renderDeviceShieldSwitchState(running: Boolean) {
        deviceShieldSwitchImage.isVisible = !running
        deviceShieldSwitchHeaderTextView.isVisible = !running
        deviceShieldSwitchSubHeaderTextView.isVisible = !running
        deviceShieldSwitchLabelTextView.isVisible = running
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
