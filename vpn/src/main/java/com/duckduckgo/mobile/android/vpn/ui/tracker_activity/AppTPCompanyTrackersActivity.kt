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
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.view.InfoPanel
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityApptpCompanyTrackersActivityBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldFAQActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.Command
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPCompanyTrackersViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.include_company_trackers_toolbar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class AppTPCompanyTrackersActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixels: DeviceShieldPixels

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: ActivityApptpCompanyTrackersActivityBinding by viewBinding()
    private val viewModel: AppTPCompanyTrackersViewModel by bindViewModel()

    private val itemsAdapter = AppTPCompanyDetailsAdapter()
    private lateinit var appEnabledSwitch: SwitchCompat

    // we might get an update before options menu has been populated; temporarily cache value to use when menu populated
    private var cachedState: ViewState? = null

    private val reportBreakage = registerForActivityResult(ReportBreakageContract()) {
        if (!it.isEmpty()) {
            Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
        }
    }

    private val toggleAppSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onAppPermissionToggled(isChecked, getPackage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        with(binding.includeToolbar) {
            setupToolbar(defaultToolbar)
            app_name.text = getAppName()
            Glide.with(applicationContext)
                .load(packageManager.safeGetApplicationIcon(getPackage()))
                .error(TextDrawable.asIconDrawable(getAppName()))
                .into(appIcon)
        }

        binding.trackingLearnMore.addClickableLink("learn_more_link", getText(R.string.atp_CompanyDetailsTrackingLearnMore)) {
            DeviceShieldFAQActivity.intent(this).also {
                startActivity(it)
            }
        }

        observeViewModel()
        binding.activityRecyclerView.adapter = itemsAdapter

        pixels.didOpenCompanyTrackersScreen()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                viewState.let {
                    renderViewState(it)
                }
            }
            .launchIn(lifecycleScope)

        lifecycleScope.launch {
            viewModel.loadData(
                getDate(),
                getPackage()
            )
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect {
                    binding.trackingAttempts.text = resources.getQuantityString(
                        R.plurals.atp_CompanyDetailsTrackingAttemptsTitle,
                        it.totalTrackingAttempts, it.totalTrackingAttempts
                    )
                    binding.includeToolbar.appTrackedAgo.text = it.lastTrackerBlockedAgo
                    itemsAdapter.updateData(it.trackingCompanies)
                }
        }

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        cachedState = viewState
        binding.trackingAttempts.text = resources.getQuantityString(
            R.plurals.atp_CompanyDetailsTrackingAttemptsTitle,
            viewState.totalTrackingAttempts, viewState.totalTrackingAttempts
        )
        binding.includeToolbar.appTrackdAgo.text = viewState.lastTrackerBlockedAgo

        lifecycleScope.launch {
            itemsAdapter.updateData(viewState.trackingCompanies)
        }

        binding.appDisabledInfoPanel.apply {
            setClickableLink(
                InfoPanel.REPORT_ISSUES_ANNOTATION,
                getText(R.string.atp_CompanyDetailsAppInfoPanel)
            ) { launchFeedback() }
            show()
        }

        if (viewState.userChangedState) {
            if (viewState.manualProtectionState) {
                binding.appDisabledInfoPanel.gone()
            } else {
                binding.appDisabledInfoPanel.show()
            }
        } else {
            setToggleState(viewState.protectionEnabled)
            if (viewState.protectionEnabled) {
                binding.appDisabledInfoPanel.gone()
            } else {
                binding.appDisabledInfoPanel.show()
            }
        }
    }

    private fun setToggleState(enabled: Boolean) {
        if (::appEnabledSwitch.isInitialized) {
            appEnabledSwitch.quietlySetIsChecked(enabled, toggleAppSwitchListener)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
        }
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch {
            TrackerBlockingVpnService.restartVpnService(applicationContext)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_company_trackers_activity, menu)

        val switchMenuItem = menu.findItem(R.id.deviceShieldSwitch)
        appEnabledSwitch = switchMenuItem?.actionView as SwitchCompat
        appEnabledSwitch.setOnCheckedChangeListener(toggleAppSwitchListener)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        cachedState?.let { vpnState ->
            appEnabledSwitch.quietlySetIsChecked(vpnState.protectionEnabled, toggleAppSwitchListener)
            cachedState = null
        }

        return super.onPrepareOptionsMenu(menu)
    }

    private fun launchFeedback() {
        reportBreakage.launch(ReportBreakageScreen.IssueDescriptionForm(getAppName(), getPackage()))
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

        fun intent(
            context: Context,
            packageName: String,
            appDisplayName: String,
            bucket: String
        ): Intent {
            val intent = Intent(context, AppTPCompanyTrackersActivity::class.java)
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(EXTRA_APP_NAME, appDisplayName)
            intent.putExtra(EXTRA_DATE, bucket)
            return intent
        }
    }
}
