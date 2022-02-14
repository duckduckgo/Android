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
import android.net.VpnService
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.Settings
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.ui.view.InfoPanel
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityDeviceShieldActivityBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldFAQActivity
import com.duckduckgo.mobile.android.vpn.ui.report.DeviceShieldAppTrackersInfo
import com.google.android.material.snackbar.Snackbar
import dummy.ui.VpnControllerActivity
import dummy.ui.VpnDiagnosticsActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DeviceShieldTrackerActivity :
    DuckDuckGoActivity(),
    DeviceShieldActivityFeedFragment.DeviceShieldActivityFeedListener,
    AppTPDisableConfirmationDialog.Listener,
    AppTPVPNConflictDialog.Listener {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels
    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private val binding: ActivityDeviceShieldActivityBinding by viewBinding()

    private lateinit var trackerBlockedCountView: PastWeekTrackerActivityContentView

    private lateinit var trackingAppsCountView: PastWeekTrackerActivityContentView
    private lateinit var ctaTrackerFaq: View
    private lateinit var deviceShieldEnabledLabel: InfoPanel
    private lateinit var deviceShieldDisabledLabel: InfoPanel
    private lateinit var deviceShieldSwitch: SwitchCompat
    private lateinit var ctaShowAll: View

    // we might get an update before options menu has been populated; temporarily cache value to use when menu populated
    private var deviceShieldCachedState: Boolean? = null

    private val feedConfig = DeviceShieldActivityFeedFragment.ActivityFeedConfig(
        maxRows = 6,
        timeWindow = 5,
        timeWindowUnits = TimeUnit.DAYS,
        showTimeWindowHeadings = false
    )

    private val viewModel: DeviceShieldTrackerActivityViewModel by bindViewModel()

    private val reportBreakage = registerForActivityResult(ReportBreakageContract()) {
        if (!it.isEmpty()) {
            Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
        }
    }

    private val enableAppTPSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppTPToggleSwitched(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.trackersToolbar)

        bindViews()
        showDeviceShieldActivity()
        observeViewModel()

        deviceShieldPixels.didShowSummaryTrackerActivity()
    }

    private fun bindViews() {
        trackerBlockedCountView = binding.trackersBlockedCount
        trackingAppsCountView = binding.trackingAppsCount
        ctaTrackerFaq = binding.ctaTrackerFaq
        deviceShieldEnabledLabel = binding.deviceShieldTrackerLabelEnabled
        deviceShieldDisabledLabel = binding.deviceShieldTrackerLabelDisabled
        ctaShowAll = binding.ctaShowAll

        binding.ctaExcludedApps.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchExcludedApps)
        }

        binding.ctaTrackerFaq.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchDeviceShieldFAQ)
        }

        binding.ctaBetaInstructions.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchBetaInstructions)
        }

        binding.ctaWhatAreAppTrackers.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchAppTrackersFAQ)
        }

        ctaShowAll.setOnClickListener {
            viewModel.onViewEvent(DeviceShieldTrackerActivityViewModel.ViewEvent.LaunchMostRecentActivity)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onVPNPermissionResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onTrackerListShowed(totalTrackers: Int) {
        if (totalTrackers >= MIN_ROWS_FOR_ALL_ACTIVITY) {
            ctaShowAll.show()
        } else {
            ctaShowAll.gone()
        }
    }

    private fun showDeviceShieldActivity() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.activity_list,
                DeviceShieldActivityFeedFragment.newInstance(feedConfig)
            )
            .commitNow()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getBlockedTrackersCount()
                .combine(viewModel.getTrackingAppsCount()) { trackers, apps ->
                    DeviceShieldTrackerActivityViewModel.TrackerCountInfo(trackers, apps)
                }
                .combine(viewModel.vpnRunningState) { trackerCountInfo, runningState ->
                    DeviceShieldTrackerActivityViewModel.TrackerActivityViewState(trackerCountInfo, runningState)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }

        }

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
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

    private fun processCommand(it: DeviceShieldTrackerActivityViewModel.Command?) {
        when (it) {
            is DeviceShieldTrackerActivityViewModel.Command.StopVPN -> stopDeviceShield()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchVPN -> startVPN()
            is DeviceShieldTrackerActivityViewModel.Command.CheckVPNPermission -> checkVPNPermission()
            is DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission -> obtainVpnRequestPermission(it.vpnIntent)
            is DeviceShieldTrackerActivityViewModel.Command.LaunchAppTrackersFAQ -> launchAppTrackersFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchBetaInstructions -> launchBetaInstructions()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchDeviceShieldFAQ -> launchDeviceShieldFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchExcludedApps -> launchExcludedApps(it.shouldListBeEnabled)
            is DeviceShieldTrackerActivityViewModel.Command.LaunchMostRecentActivity -> launchMostRecentActivity()
            is DeviceShieldTrackerActivityViewModel.Command.ShowDisableConfirmationDialog -> launchDisableConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowVpnConflictDialog -> launchVPNConflictDialog()
        }
    }

    private fun launchExcludedApps(shouldListBeEnabled: Boolean) {
        startActivity(TrackingProtectionExclusionListActivity.intent(this, shouldListBeEnabled))
    }

    private fun launchDeviceShieldFAQ() {
        startActivity(DeviceShieldFAQActivity.intent(this))
    }

    private fun launchDisableConfirmationDialog() {
        deviceShieldSwitch.quietlySetIsChecked(true, enableAppTPSwitchListener)
        deviceShieldPixels.didShowDisableTrackingProtectionDialog()
        val dialog = AppTPDisableConfirmationDialog.instance()
        dialog.show(
            supportFragmentManager,
            AppTPDisableConfirmationDialog.TAG_APPTP_DISABLE_DIALOG
        )
    }

    private fun launchVPNConflictDialog() {
        deviceShieldSwitch.quietlySetIsChecked(false, enableAppTPSwitchListener)
        deviceShieldPixels.didShowVpnConflictDialog()
        val dialog = AppTPVPNConflictDialog.instance()
        dialog.show(
            supportFragmentManager,
            AppTPVPNConflictDialog.TAG_VPN_CONFLICT_DIALOG
        )
    }

    override fun onOpenAppProtection() {
        deviceShieldPixels.didChooseToDisableOneAppFromDialog()
        launchExcludedApps(viewModel.vpnRunningState.value.isRunning)
    }

    override fun onTurnAppTrackingProtectionOff() {
        deviceShieldSwitch.quietlySetIsChecked(false, enableAppTPSwitchListener)
        deviceShieldPixels.didChooseToDisableTrackingProtectionFromDialog()
        viewModel.onAppTpManuallyDisabled()
    }

    override fun onDisableDialogCancelled() {
        deviceShieldPixels.didChooseToCancelTrackingProtectionDialog()
    }

    override fun onDismissConflictDialog() {
        deviceShieldPixels.didChooseToDismissVpnConflicDialog()
    }

    override fun onOpenSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflicDialog()

        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun launchBetaInstructions() {
        val intent = Intent(this, Class.forName("com.duckduckgo.app.browser.webview.WebViewActivity"))
        intent.putExtra("URL_EXTRA", getString(R.string.atp_WaitlistBetaBlogPost))
        intent.putExtra("TITLE_EXTRA", getString(R.string.atp_ActivityBetaInstructions))
        startActivity(intent)
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                deviceShieldPixels.enableFromSummaryTrackerActivity()
                startVPN()
            }
            is VpnPermissionStatus.Denied -> {
                viewModel.onVPNPermissionNeeded(permissionStatus.intent)
            }
        }
    }

    private fun checkVpnPermissionStatus(): VpnPermissionStatus {
        val intent = VpnService.prepare(applicationContext)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun launchAppTrackersFAQ() {
        startActivity(DeviceShieldAppTrackersInfo.intent(this))
    }

    private fun launchMostRecentActivity() {
        deviceShieldPixels.didShowDetailedTrackerActivity()
        startActivity(DeviceShieldMostRecentActivity.intent(this))
    }

    private fun startVPN() {
        deviceShieldSwitch.quietlySetIsChecked(true, enableAppTPSwitchListener)
        TrackerBlockingVpnService.startService(this)
    }

    private fun stopDeviceShield() {
        deviceShieldSwitch.quietlySetIsChecked(false, enableAppTPSwitchListener)
        TrackerBlockingVpnService.stopService(this)
    }

    private fun renderViewState(state: DeviceShieldTrackerActivityViewModel.TrackerActivityViewState) {
        // is there a better way to do this?
        if (::deviceShieldSwitch.isInitialized) {
            deviceShieldSwitch.quietlySetIsChecked(state.runningState.isRunning, enableAppTPSwitchListener)
        } else {
            Timber.v("switch view reference not yet initialized; cache value until menu populated")
            deviceShieldCachedState = state.runningState.isRunning
        }

        updateCounts(state.trackerCountInfo)
        updateRunningState(state.runningState)
    }

    private fun updateCounts(trackerCountInfo: DeviceShieldTrackerActivityViewModel.TrackerCountInfo) {
        trackerBlockedCountView.count = trackerCountInfo.stringTrackerCount()
        trackerBlockedCountView.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekTrackerCount, trackerCountInfo.trackers.value)

        trackingAppsCountView.count = trackerCountInfo.stringAppsCount()
        trackingAppsCountView.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekAppCount, trackerCountInfo.apps.value)
    }

    private fun updateRunningState(state: RunningState) {
        if (state.isRunning) {
            deviceShieldDisabledLabel.gone()
            deviceShieldEnabledLabel.show()
            deviceShieldEnabledLabel.apply {
                setClickableLink(
                    REPORT_ISSUES_ANNOTATION,
                    getText(R.string.atp_ActivityEnabledLabel)
                ) { launchFeedback() }
            }
        } else {
            deviceShieldEnabledLabel.gone()
            deviceShieldDisabledLabel.show()
            deviceShieldDisabledLabel.apply {
                setClickableLink(
                    REPORT_ISSUES_ANNOTATION,
                    getText(R.string.atp_ActivityDisabledLabel)
                ) { launchFeedback() }
            }
        }
    }

    private fun addClickableLink(
        annotation: String,
        text: CharSequence,
        onClick: () -> Unit
    ): SpannableString {
        val fullText = text as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    UnderlineSpan(),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(baseContext, com.duckduckgo.mobile.android.R.color.almostBlackDark)
                    ),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannableString
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_tracker_activity, menu)

        val switchMenuItem = menu.findItem(R.id.deviceShieldSwitch)
        deviceShieldSwitch = switchMenuItem?.actionView as SwitchCompat
        deviceShieldSwitch.setOnCheckedChangeListener(enableAppTPSwitchListener)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.customDnsServer)?.let {
            it.isChecked = viewModel.isCustomDnsServerSet()
            it.isEnabled = !TrackerBlockingVpnService.isServiceRunning(this)
        }
        menu.findItem(R.id.diagnosticsScreen).isVisible = appBuildConfig.isDebug
        menu.findItem(R.id.dataScreen).isVisible = appBuildConfig.isDebug
        menu.findItem(R.id.customDnsServer).isVisible = appBuildConfig.isDebug

        deviceShieldCachedState?.let { checked ->
            deviceShieldSwitch.quietlySetIsChecked(checked, enableAppTPSwitchListener)
            deviceShieldCachedState = null
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.dataScreen -> {
                startActivity(VpnControllerActivity.intent(this)); true
            }
            R.id.diagnosticsScreen -> {
                startActivity(VpnDiagnosticsActivity.intent(this)); true
            }
            R.id.customDnsServer -> {
                val enabled = !item.isChecked
                viewModel.useCustomDnsServer(enabled)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private fun launchFeedback() {
        deviceShieldPixels.didSubmitReportIssuesFromTrackerActivity()
        reportBreakage.launch(ReportBreakageScreen.ListOfInstalledApps)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA"
        private const val ON_LAUNCHED_CALLED_SUCCESS = 0
        private const val MIN_ROWS_FOR_ALL_ACTIVITY = 6

        private const val REQUEST_ASK_VPN_PERMISSION = 101
        private const val REPORT_ISSUES_ANNOTATION = "report_issues_link"

        fun intent(
            context: Context,
            onLaunchCallback: ResultReceiver? = null
        ): Intent {
            return Intent(context, DeviceShieldTrackerActivity::class.java).apply {
                putExtra(RESULT_RECEIVER_EXTRA, onLaunchCallback)
            }
        }
    }

}
