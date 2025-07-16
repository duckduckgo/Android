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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.ResultReceiver
import android.view.Menu
import android.view.View
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.notifyme.NotifyMeView
import com.duckduckgo.common.ui.view.DaxDialogListener
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.TypewriterDaxDialog
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.launchAlwaysOnSystemSettings
import com.duckduckgo.common.utils.plugins.ActivePlugin
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.apps.ui.ManageRecentAppsProtectionActivity
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityDeviceShieldActivityBinding
import com.duckduckgo.mobile.android.vpn.di.AppTpBreakageCategories
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.alwayson.AlwaysOnAlertDialogFragment
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.DeviceShieldAppTrackersInfo
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivityViewModel.ViewEvent
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivityViewModel.ViewEvent.StartVpn
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.DisableVpnDialogOptions
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view.message.AppTPStateMessagePlugin.DefaultAppTPMessageAction
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AppTrackerActivityWithEmptyParams::class, screenName = "apptp.main")
class DeviceShieldTrackerActivity :
    DuckDuckGoActivity(),
    DeviceShieldActivityFeedFragment.DeviceShieldActivityFeedListener {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var reportBreakageContract: Provider<ReportBreakageContract>

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppTpBreakageCategories
    lateinit var breakageCategories: List<AppBreakageCategory>

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appTPStateMessagePluginPoint: ActivePluginPoint<AppTPStateMessagePlugin>

    private val binding: ActivityDeviceShieldActivityBinding by viewBinding()

    private lateinit var deviceShieldSwitch: DaxSwitch

    // we might get an update before options menu has been populated; temporarily cache value to use when menu populated
    private var vpnCachedState: VpnState? = null

    private val feedConfig = DeviceShieldActivityFeedFragment.ActivityFeedConfig(
        maxRows = MIN_ROWS_FOR_ALL_ACTIVITY,
        timeWindow = 7,
        timeWindowUnits = TimeUnit.DAYS,
        showTimeWindowHeadings = false,
    )

    private val viewModel: DeviceShieldTrackerActivityViewModel by bindViewModel()

    private lateinit var reportBreakage: ActivityResultLauncher<ReportBreakageScreen>

    private val enableAppTPSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppTPToggleSwitched(isChecked)
    }

    private val onInfoMessageClick = fun(action: DefaultAppTPMessageAction) {
        when (action) {
            DefaultAppTPMessageAction.ReenableAppTP -> reEnableAppTrackingProtection()
            DefaultAppTPMessageAction.LaunchFeedback -> launchFeedback()
            DefaultAppTPMessageAction.HandleAlwaysOnActionRequired -> launchAlwaysOnLockdownEnabledDialog()
        }
    }

    private var currenActivePlugin: ActivePlugin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportBreakage = registerForActivityResult(reportBreakageContract.get()) {
            if (!it.isEmpty()) {
                Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
            }
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.trackersToolbar)

        bindViews()
        showDeviceShieldActivity()
        observeViewModel()

        deviceShieldPixels.didShowSummaryTrackerActivity()
    }

    private fun bindViews() {
        binding.ctaTrackerFaq.setClickListener {
            viewModel.onViewEvent(ViewEvent.LaunchDeviceShieldFAQ)
        }

        binding.ctaWhatAreAppTrackers.setClickListener {
            viewModel.onViewEvent(ViewEvent.LaunchAppTrackersFAQ)
        }

        binding.ctaManageProtection.setClickListener {
            viewModel.onViewEvent(ViewEvent.LaunchExcludedApps)
        }

        binding.ctaManageViewAllApps.setClickListener {
            viewModel.onViewEvent(ViewEvent.LaunchTrackingProtectionExclusionListActivity)
        }

        binding.ctaRemoveFeature.setClickListener {
            viewModel.onViewEvent(ViewEvent.AskToRemoveFeature)
        }

        binding.ctaShowAll.setOnClickListener {
            viewModel.onViewEvent(ViewEvent.LaunchMostRecentActivity)
        }

        binding.deviceShieldTrackerNotifyMe.setOnVisibilityChange(
            object : NotifyMeView.OnVisibilityChangedListener {
                override fun onVisibilityChange(
                    v: View?,
                    isVisible: Boolean,
                ) {
                    if (isVisible) {
                        binding.deviceShieldTrackerMessageContainer.gone()
                    } else {
                        binding.deviceShieldTrackerMessageContainer.show()
                    }
                }
            },
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            viewModel.onVPNPermissionResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onTrackerListShowed(totalTrackers: Int) {
        if (totalTrackers >= MIN_ROWS_FOR_ALL_ACTIVITY) {
            binding.ctaShowAll.show()
        } else {
            binding.ctaShowAll.gone()
        }
        viewModel.showAppTpEnabledCtaIfNeeded()
    }

    private fun showDeviceShieldActivity() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.activity_list,
                DeviceShieldActivityFeedFragment.newInstance(feedConfig),
            )
            .commitNow()
    }

    @OptIn(FlowPreview::class)
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getBlockedTrackersCount()
                .combine(viewModel.getTrackingAppsCount()) { trackers, apps ->
                    DeviceShieldTrackerActivityViewModel.TrackerCountInfo(trackers, apps)
                }
                .combine(viewModel.getRunningState()) { trackerCountInfo, runningState ->
                    DeviceShieldTrackerActivityViewModel.TrackerActivityViewState(trackerCountInfo, runningState)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }

        lifecycleScope.launch {
            // This is a one-shot check as soon as the screen is shown
            viewModel.getRunningState()
                .map { it.alwaysOnState }
                .debounce(500) // give a bit of time so that pop doesn't just suddenly pops up
                .take(1)
                // we do this on CREATED because we don't want to show the dialogs when user leaves app and switches back here
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect {
                    viewModel.onViewEvent(ViewEvent.AlwaysOnInitialState(it))
                }
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
        super.onBackPressed()
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun processCommand(it: DeviceShieldTrackerActivityViewModel.Command) {
        when (it) {
            is DeviceShieldTrackerActivityViewModel.Command.StopVPN -> stopDeviceShield()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchVPN -> startVPN()
            is DeviceShieldTrackerActivityViewModel.Command.CheckVPNPermission -> checkVPNPermission()
            is DeviceShieldTrackerActivityViewModel.Command.RequestVPNPermission -> obtainVpnRequestPermission(it.vpnIntent)
            is DeviceShieldTrackerActivityViewModel.Command.LaunchAppTrackersFAQ -> launchAppTrackersFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchDeviceShieldFAQ -> launchDeviceShieldFAQ()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchManageAppsProtection -> launchManageAppsProtection()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchMostRecentActivity -> launchMostRecentActivity()
            is DeviceShieldTrackerActivityViewModel.Command.ShowDisableVpnConfirmationDialog -> launchDisableConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowVpnConflictDialog -> showVpnConflictDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowVpnAlwaysOnConflictDialog -> showAlwaysOnConflictDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowAlwaysOnPromotionDialog -> launchAlwaysOnPromotionDialog()
            is DeviceShieldTrackerActivityViewModel.Command.ShowAlwaysOnLockdownWarningDialog -> launchAlwaysOnLockdownEnabledDialog()
            is DeviceShieldTrackerActivityViewModel.Command.VPNPermissionNotGranted -> quietlyToggleAppTpSwitch(false)
            is DeviceShieldTrackerActivityViewModel.Command.ShowRemoveFeatureConfirmationDialog -> launchRemoveFeatureConfirmationDialog()
            is DeviceShieldTrackerActivityViewModel.Command.CloseScreen -> finish()
            is DeviceShieldTrackerActivityViewModel.Command.OpenVpnSettings -> openVPNSettings()
            is DeviceShieldTrackerActivityViewModel.Command.ShowAppTpEnabledCta -> showAppTpEnabledCta()
            is DeviceShieldTrackerActivityViewModel.Command.LaunchTrackingProtectionExclusionListActivity ->
                launchTrackingProtectionExclusionListActivity()
        }
    }

    private fun launchManageAppsProtection() {
        deviceShieldPixels.didOpenManageRecentAppSettings()
        startActivity(ManageRecentAppsProtectionActivity.intent(this))
    }

    private fun launchDeviceShieldFAQ() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = FAQ_WEBSITE,
                screenTitle = getString(R.string.atp_FAQActivityTitle),
            ),
        )
    }

    private fun launchDisableConfirmationDialog() {
        deviceShieldSwitch.quietlySetIsChecked(true, enableAppTPSwitchListener)
        deviceShieldPixels.didShowDisableTrackingProtectionDialog()

        StackedAlertDialogBuilder(this)
            .setTitle(R.string.atp_DisableConfirmationDialogTitle)
            .setMessage(getString(R.string.atp_DisableConfirmationDialogMessage))
            .setStackedButtons(DisableVpnDialogOptions.asOptions())
            .addEventListener(
                object : StackedAlertDialogBuilder.EventListener() {
                    override fun onButtonClicked(position: Int) {
                        when (DisableVpnDialogOptions.getOptionFromPosition(position)) {
                            DisableVpnDialogOptions.DISABLE_APP -> onOpenAppProtection()
                            DisableVpnDialogOptions.DISABLE_VPN -> onTurnAppTrackingProtectionOff()
                            DisableVpnDialogOptions.CANCEL -> onDisableDialogCancelled()
                        }
                    }
                },
            )
            .show()
    }

    private fun launchRemoveFeatureConfirmationDialog() {
        deviceShieldPixels.didShowRemoveTrackingProtectionFeatureDialog()

        TextAlertDialogBuilder(this)
            .setTitle(R.string.atp_RemoveFeatureDialogTitle)
            .setMessage(R.string.atp_RemoveFeatureDialogMessage)
            .setPositiveButton(R.string.atp_RemoveFeatureDialogRemove, DESTRUCTIVE)
            .setNegativeButton(R.string.atp_RemoveFeatureDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.removeFeature()
                    }

                    override fun onNegativeButtonClicked() {
                        deviceShieldPixels.didChooseToCancelRemoveTrakcingProtectionDialog()
                    }
                },
            )
            .show()
    }

    private fun showVpnConflictDialog() {
        quietlyToggleAppTpSwitch(false)
        deviceShieldPixels.didShowVpnConflictDialog()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.atp_VpnConflictDialogTitle)
            .setMessage(R.string.atp_VpnConflictDialogMessage)
            .setPositiveButton(R.string.atp_VpnConflictDialogGotIt)
            .setNegativeButton(R.string.atp_VpnConflictDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogContinue()
                    }

                    override fun onNegativeButtonClicked() {
                        onVpnConflictDialogDismiss()
                    }
                },
            )
            .show()
    }

    private fun showAlwaysOnConflictDialog() {
        deviceShieldPixels.didShowVpnConflictDialog()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.atp_VpnConflictAlwaysOnDialogTitle)
            .setMessage(R.string.atp_VpnConflictDialogAlwaysOnMessage)
            .setPositiveButton(R.string.atp_VpnConflictDialogOpenSettings)
            .setNegativeButton(R.string.atp_VpnConflictDialogCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        onVpnConflictDialogGoToSettings()
                    }

                    override fun onNegativeButtonClicked() {
                        onVpnConflictDialogDismiss()
                    }
                },
            )
            .show()
    }

    private fun launchAlwaysOnPromotionDialog() {
        val dialog = supportFragmentManager.findFragmentByTag(TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG) as? AlwaysOnAlertDialogFragment
        dialog?.dismiss()

        AlwaysOnAlertDialogFragment.newAlwaysOnDialog(
            object : AlwaysOnAlertDialogFragment.Listener {
                override fun onGoToSettingsClicked() {
                    viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnOpenSettings)
                }

                override fun onCanceled() {
                    viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnCancelled)
                }
            },
        ).show(supportFragmentManager, TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG)
    }

    private fun launchAlwaysOnLockdownEnabledDialog() {
        val dialog = supportFragmentManager.findFragmentByTag(TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG) as? AlwaysOnAlertDialogFragment
        dialog?.dismiss()

        AlwaysOnAlertDialogFragment.newAlwaysOnLockdownDialog(
            object : AlwaysOnAlertDialogFragment.Listener {
                override fun onGoToSettingsClicked() {
                    viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnOpenSettings)
                }

                override fun onCanceled() {
                    viewModel.onViewEvent(ViewEvent.PromoteAlwaysOnCancelled)
                }
            },
        ).show(supportFragmentManager, TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG)
    }

    fun onOpenAppProtection() {
        deviceShieldPixels.didChooseToDisableOneAppFromDialog()
        viewModel.onViewEvent(ViewEvent.LaunchExcludedApps)
    }

    fun onTurnAppTrackingProtectionOff() {
        quietlyToggleAppTpSwitch(false)
        deviceShieldPixels.didChooseToDisableTrackingProtectionFromDialog()
        viewModel.onAppTpManuallyDisabled()
    }

    fun onDisableDialogCancelled() {
        deviceShieldPixels.didChooseToCancelTrackingProtectionDialog()
    }

    fun onVpnConflictDialogDismiss() {
        deviceShieldPixels.didChooseToDismissVpnConflictDialog()
    }

    fun onVpnConflictDialogGoToSettings() {
        deviceShieldPixels.didChooseToOpenSettingsFromVpnConflictDialog()
        openVPNSettings()
    }

    @SuppressLint("InlinedApi")
    private fun openVPNSettings() {
        this.launchAlwaysOnSystemSettings()
    }

    fun onVpnConflictDialogContinue() {
        deviceShieldPixels.didChooseToContinueFromVpnConflictDialog()
        checkVPNPermission()
    }

    private fun checkVPNPermission() {
        when (val permissionStatus = checkVpnPermissionStatus()) {
            is VpnPermissionStatus.Granted -> {
                viewModel.onViewEvent(StartVpn)
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

    private fun launchTrackingProtectionExclusionListActivity() {
        startActivity(TrackingProtectionExclusionListActivity.intent(this))
    }

    private fun startVPN() {
        quietlyToggleAppTpSwitch(true)
        lifecycleScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.registerFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    private fun stopDeviceShield() {
        quietlyToggleAppTpSwitch(false)
        lifecycleScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    private fun quietlyToggleAppTpSwitch(state: Boolean) {
        deviceShieldSwitch.quietlySetIsChecked(state, enableAppTPSwitchListener)
    }

    private suspend fun renderViewState(state: DeviceShieldTrackerActivityViewModel.TrackerActivityViewState) {
        vpnCachedState = state.runningState
        if (::deviceShieldSwitch.isInitialized) {
            quietlyToggleAppTpSwitch(state.runningState.state == VpnRunningState.ENABLED)
        } else {
            logcat { "switch view reference not yet initialized; cache value until menu populated" }
        }

        updateCounts(state.trackerCountInfo)
        updateRunningState(state.runningState)
    }

    private fun updateCounts(trackerCountInfo: DeviceShieldTrackerActivityViewModel.TrackerCountInfo) {
        binding.trackersBlockedCount.count = trackerCountInfo.stringTrackerCount()
        binding.trackersBlockedCount.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekTrackerCount, trackerCountInfo.trackers.value)

        binding.trackingAppsCount.count = trackerCountInfo.stringAppsCount()
        binding.trackingAppsCount.footer =
            resources.getQuantityString(R.plurals.atp_ActivityPastWeekAppCount, trackerCountInfo.apps.value)
    }

    private suspend fun updateRunningState(runningState: VpnState) {
        if (!binding.deviceShieldTrackerNotifyMe.isVisible) {
            var newActivePlugin: ActivePlugin? = null
            appTPStateMessagePluginPoint.getPlugins().firstNotNullOfOrNull {
                it.getView(this, runningState, onInfoMessageClick)?.apply {
                    newActivePlugin = it
                }
            }?.let {
                if (currenActivePlugin == null || newActivePlugin != currenActivePlugin) {
                    currenActivePlugin = newActivePlugin
                    binding.deviceShieldTrackerMessageContainer.show()
                    binding.deviceShieldTrackerMessageContainer.removeAllViews()
                    binding.deviceShieldTrackerMessageContainer.addView(it)
                }
            } ?: {
                currenActivePlugin = null
                binding.deviceShieldTrackerMessageContainer.gone()
            }
        } else {
            currenActivePlugin = null
            binding.deviceShieldTrackerMessageContainer.gone()
        }

        if (runningState.state == VpnRunningState.ENABLED) {
            binding.deviceShieldTrackerBlockingTrackersDescription.text =
                resources.getString(R.string.atp_ActivityBlockingTrackersEnabledDescription)
            binding.deviceShieldTrackerShieldImage.setImageResource(R.drawable.apptp_shield_enabled)
        } else {
            binding.deviceShieldTrackerBlockingTrackersDescription.text =
                resources.getString(R.string.atp_ActivityBlockingTrackersDisabledDescription)
            binding.deviceShieldTrackerShieldImage.setImageResource(R.drawable.apptp_shield_disabled)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_tracker_activity, menu)

        val switchMenuItem = menu.findItem(R.id.deviceShieldSwitch)
        deviceShieldSwitch = switchMenuItem?.actionView as DaxSwitch
        deviceShieldSwitch.setOnCheckedChangeListener(enableAppTPSwitchListener)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        vpnCachedState?.let { vpnState ->
            deviceShieldSwitch.quietlySetIsChecked(vpnState.state == VpnRunningState.ENABLED, enableAppTPSwitchListener)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private fun launchFeedback() {
        deviceShieldPixels.didSubmitReportIssuesFromTrackerActivity()
        reportBreakage.launch(ReportBreakageScreen.ListOfInstalledApps("apptp", breakageCategories))
    }

    private fun reEnableAppTrackingProtection() {
        checkVPNPermission()
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    private fun showAppTpEnabledCta() {
        supportFragmentManager.findFragmentByTag(TAG_APPTP_ENABLED_CTA_DIALOG)?.let {
            return
        }

        val dialog = TypewriterDaxDialog.newInstance(
            daxText = getString(R.string.atp_ActivityAppTpEnabledCtaText),
            primaryButtonText = getString(R.string.atp_ActivityAppTpEnabledCtaButtonLabel),
            hideButtonText = "",
        )

        dialog.setDaxDialogListener(
            object : DaxDialogListener {
                override fun onDaxDialogDismiss() {
                    // NO OP
                }

                override fun onDaxDialogPrimaryCtaClick() {
                    dialog.dismiss()
                    launchKonfetti()
                    deviceShieldPixels.didPressOnAppTpEnabledCtaButton()
                }

                override fun onDaxDialogSecondaryCtaClick() {
                    // NO OP
                }

                override fun onDaxDialogHideClick() {
                    // NO OP
                }
            },
        )

        dialog.show(supportFragmentManager, TAG_APPTP_ENABLED_CTA_DIALOG)
    }

    private fun launchKonfetti() {
        val magenta = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.magenta, null)
        val blue = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.blue30, null)
        val purple = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.purple, null)
        val green = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.green, null)
        val yellow = ResourcesCompat.getColor(getResources(), com.duckduckgo.mobile.android.R.color.yellow, null)

        val displayWidth = resources.displayMetrics.widthPixels

        binding.appTpEnabledKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(4f, 9f)
            .setFadeOutEnabled(true)
            .setTimeToLive(1500L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
            .streamFor(60, 2000L)
    }

    companion object {
        private const val RESULT_RECEIVER_EXTRA = "RESULT_RECEIVER_EXTRA"
        private const val ON_LAUNCHED_CALLED_SUCCESS = 0
        private const val MIN_ROWS_FOR_ALL_ACTIVITY = 5
        private const val TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG = "AppTPPromoteAlwaysOnDialog"
        private const val TAG_APPTP_ENABLED_CTA_DIALOG = "AppTpEnabledCta"
        private const val FAQ_WEBSITE = "https://help.duckduckgo.com/duckduckgo-help-pages/p-app-tracking-protection/what-is-app-tracking-protection/"

        private const val REQUEST_ASK_VPN_PERMISSION = 101

        internal fun intent(
            context: Context,
            onLaunchCallback: ResultReceiver? = null,
        ): Intent {
            return Intent(context, DeviceShieldTrackerActivity::class.java).apply {
                putExtra(RESULT_RECEIVER_EXTRA, onLaunchCallback)
            }
        }
    }
}
