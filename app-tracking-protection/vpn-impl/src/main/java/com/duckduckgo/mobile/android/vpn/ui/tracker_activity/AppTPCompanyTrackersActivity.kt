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
import android.view.Menu
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.browser.api.ui.WebViewActivityWithParams
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.InfoPanel
import com.duckduckgo.mobile.android.ui.view.SwitchView
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.setEnabledOpacity
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature.APPTP_VPN
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.R.string
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityApptpCompanyTrackersActivityBinding
import com.duckduckgo.mobile.android.vpn.di.AppTpBreakageCategories
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.BannerState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.BannerState.NONE
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.BannerState.SHOW_UNPROTECTED
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.BannerState.SHOW_UNPROTECTED_THROUGH_NETP
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.Command
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.ViewState
import com.duckduckgo.mobile.android.vpn.ui.util.TextDrawable
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetPAppExclusionListNoParams
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@InjectWith(ActivityScope::class)
class AppTPCompanyTrackersActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixels: DeviceShieldPixels

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var reportBreakageContract: Provider<ReportBreakageContract>

    @Inject
    @AppTpBreakageCategories
    lateinit var breakageCategories: List<AppBreakageCategory>

    private val binding: ActivityApptpCompanyTrackersActivityBinding by viewBinding()
    private val viewModel: AppTPCompanyTrackersViewModel by bindViewModel()

    private val itemsAdapter = AppTPCompanyDetailsAdapter()
    private lateinit var appEnabledSwitch: SwitchCompat

    // we might get an update before options menu has been populated; temporarily cache value to use when menu populated
    private var cachedState: ViewState? = null

    private lateinit var reportBreakage: ActivityResultLauncher<ReportBreakageScreen>

    private val toggleAppSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppPermissionToggled(isChecked, getPackage())
    }

    private val isAppTPEnabled by lazy {
        runBlocking {
            vpnFeaturesRegistry.isFeatureRegistered(APPTP_VPN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportBreakage = registerForActivityResult(reportBreakageContract.get()) {
            if (!it.isEmpty()) {
                Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
            }
        }

        setContentView(binding.root)
        with(binding.includeToolbar) {
            setupToolbar(defaultToolbar)
            appName.text = getAppName()
            Glide.with(applicationContext)
                .load(packageManager.safeGetApplicationIcon(getPackage()))
                .error(TextDrawable.asIconDrawable(getAppName()))
                .into(appIcon)
        }

        binding.trackingLearnMore.addClickableLink("learn_more_link", getText(R.string.atp_CompanyDetailsTrackingLearnMore)) {
            globalActivityStarter.start(
                this,
                WebViewActivityWithParams(
                    url = FAQ_WEBSITE,
                    screenTitle = getString(string.atp_FAQActivityTitle),
                ),
            )
        }

        observeViewModel()
        binding.activityRecyclerView.adapter = itemsAdapter

        pixels.didOpenCompanyTrackersScreen()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                renderViewState(viewState)
            }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            viewModel.loadData(
                getDate(),
                getPackage(),
            )
        }
    }

    private fun renderViewState(viewState: ViewState) {
        cachedState = viewState
        binding.trackingAttempts.primaryText = resources.getQuantityString(
            R.plurals.atp_CompanyDetailsTrackingAttemptsTitle,
            viewState.totalTrackingAttempts,
            viewState.totalTrackingAttempts,
        )
        binding.includeToolbar.appTrackedAgo.text = viewState.lastTrackerBlockedAgo

        lifecycleScope.launch {
            itemsAdapter.updateData(viewState.trackingCompanies)
        }

        setToggleState(viewState.toggleChecked, viewState.toggleEnabled && isAppTPEnabled)
        binding.handleProtectionState(viewState.bannerState)
    }

    private fun ActivityApptpCompanyTrackersActivityBinding.handleProtectionState(bannerState: BannerState) {
        when (bannerState) {
            NONE -> appDisabledInfoPanel.gone()
            SHOW_UNPROTECTED -> {
                appDisabledInfoPanel.setClickableLink(
                    InfoPanel.REPORT_ISSUES_ANNOTATION,
                    getText(R.string.atp_CompanyDetailsAppInfoPanel),
                ) { launchFeedback() }
                appDisabledInfoPanel.show()
            }
            SHOW_UNPROTECTED_THROUGH_NETP -> {
                appDisabledInfoPanel.setClickableLink(
                    MANAGE_APP_EXCLUSIONS,
                    getText(R.string.atp_CompanyDetailsBannerForUnprotectedThroughNetP),
                ) { launchManageAppExclusions() }
                appDisabledInfoPanel.show()
            }
        }
    }

    private fun setToggleState(checked: Boolean, enabled: Boolean) {
        if (::appEnabledSwitch.isInitialized) {
            appEnabledSwitch.quietlySetIsChecked(checked, toggleAppSwitchListener)
            appEnabledSwitch.isEnabled = enabled
            appEnabledSwitch.setEnabledOpacity(enabled)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
        }
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_company_trackers_activity, menu)

        val switchMenuItem = menu.findItem(R.id.deviceShieldSwitch)
        appEnabledSwitch = switchMenuItem?.actionView as SwitchView
        appEnabledSwitch.setOnCheckedChangeListener(toggleAppSwitchListener)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        cachedState?.let { vpnState ->
            appEnabledSwitch.quietlySetIsChecked(vpnState.toggleChecked, toggleAppSwitchListener)
            appEnabledSwitch.isEnabled = vpnState.toggleEnabled && isAppTPEnabled
            appEnabledSwitch.setEnabledOpacity(vpnState.toggleEnabled && isAppTPEnabled)
            cachedState = null
        }

        return super.onPrepareOptionsMenu(menu)
    }

    private fun launchFeedback() {
        reportBreakage.launch(ReportBreakageScreen.IssueDescriptionForm("apptp", breakageCategories, getAppName(), getPackage()))
    }

    private fun launchManageAppExclusions() {
        globalActivityStarter.start(this, NetPAppExclusionListNoParams)
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getDate(): String {
        return intent.getStringExtra(EXTRA_DATE)!!
    }

    private fun getAppName(): String {
        return intent.getStringExtra(EXTRA_APP_NAME)!!
    }

    private fun getPackage(): String {
        return intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        private const val EXTRA_APP_NAME = "EXTRA_APP_NAME"
        private const val EXTRA_DATE = "EXTRA_DATE"
        private const val FAQ_WEBSITE = "https://help.duckduckgo.com/duckduckgo-help-pages/p-app-tracking-protection/what-is-app-tracking-protection/"
        private const val MANAGE_APP_EXCLUSIONS = "manage_app_exclusion_link"

        internal fun intent(
            context: Context,
            packageName: String,
            appDisplayName: String,
            bucket: String,
        ): Intent {
            val intent = Intent(context, AppTPCompanyTrackersActivity::class.java)
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(EXTRA_APP_NAME, appDisplayName)
            intent.putExtra(EXTRA_DATE, bucket)
            return intent
        }
    }
}
