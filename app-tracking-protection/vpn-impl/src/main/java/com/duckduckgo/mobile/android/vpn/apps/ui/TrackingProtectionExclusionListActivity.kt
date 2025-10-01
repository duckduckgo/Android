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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.*
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R as commonR
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature.APPTP_VPN
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.R.string
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.apps.Command
import com.duckduckgo.mobile.android.vpn.apps.ManageAppsProtectionViewModel
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.apps.ui.ExclusionListAdapter.ExclusionListListener
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityTrackingProtectionExclusionListBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class TrackingProtectionExclusionListActivity :
    DuckDuckGoActivity(),
    ManuallyEnableAppProtectionDialog.ManuallyEnableAppsProtectionDialogListener,
    ManuallyDisableAppProtectionDialog.ManuallyDisableAppProtectionDialogListener,
    RestoreDefaultProtectionDialog.RestoreDefaultProtectionDialogListener {

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var reportBreakageContract: Provider<ReportBreakageContract>

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityTrackingProtectionExclusionListBinding by viewBinding()

    private val viewModel: ManageAppsProtectionViewModel by bindViewModel()

    lateinit var adapter: ExclusionListAdapter

    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.deviceShieldExclusionAppListSkeleton) }

    private lateinit var reportBreakage: ActivityResultLauncher<ReportBreakageScreen>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportBreakage = registerForActivityResult(reportBreakageContract.get()) { result ->
            if (!result.isEmpty()) {
                Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, LENGTH_LONG).show()
            }
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupRecycler()

        bindViews()
        observeViewModel()

        viewModel.applyAppsFilter(getAppsFilterOrDefault())

        deviceShieldPixels.didShowExclusionListActivity()

        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_exclusion_list_activity, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val restoreDefault = menu.findItem(R.id.restoreDefaults)
        // onPrepareOptionsMenu is called when overflow menu is being displayed, that's why this can be an imperative call
        restoreDefault?.isEnabled = viewModel.canRestoreDefaults()

        val textColorAttr = if (viewModel.canRestoreDefaults()) commonR.attr.daxColorPrimaryText else commonR.attr.daxColorTextDisabled
        val spannable = SpannableString(restoreDefault.title)
        spannable.setSpan(ForegroundColorSpan(binding.root.context.getColorFromAttr(textColorAttr)), 0, spannable.length, 0)
        restoreDefault.title = spannable

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.restoreDefaults -> {
                val dialog = RestoreDefaultProtectionDialog.instance()
                dialog.show(supportFragmentManager, RestoreDefaultProtectionDialog.TAG_RESTORE_DEFAULT_PROTECTION)
                true
            }

            R.id.reportIssue -> {
                launchFeedback()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bindViews() {
        shimmerLayout.startShimmer()
        setupRecycler()
    }

    private fun setupRecycler() {
        adapter = ExclusionListAdapter(
            object : ExclusionListListener {
                override fun onAppProtectionChanged(
                    excludedAppInfo: TrackingProtectionAppInfo,
                    enabled: Boolean,
                    position: Int,
                ) {
                    viewModel.onAppProtectionChanged(excludedAppInfo, position, enabled)
                }

                override fun onLaunchFAQ() {
                    launchFaq()
                }

                override fun onLaunchFeedback() {
                    launchFeedback()
                }

                override fun onFilterClick(anchorView: View) {
                    showFilterPopupMenu(anchorView)
                }
            },
        )

        binding.excludedAppsRecycler.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getProtectedApps()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        shimmerLayout.stopShimmer()
        adapter.update(viewState)
        shimmerLayout.gone()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.RestartVpn -> restartVpn()
            is Command.ShowDisableProtectionDialog -> showDisableProtectionDialog(
                command.excludingReason,
            )

            is Command.ShowEnableProtectionDialog -> showEnableProtectionDialog(
                command.excludingReason,
                command.position,
            )

            is Command.LaunchFeedback -> reportBreakage.launch(command.reportBreakageScreen)
            else -> { /* noop */
            }
        }
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (vpnFeaturesRegistry.isFeatureRegistered(APPTP_VPN)) {
                vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
            }
        }
    }

    private fun showDisableProtectionDialog(excludedAppInfo: TrackingProtectionAppInfo) {
        val dialog = ManuallyDisableAppProtectionDialog.instance(excludedAppInfo)
        dialog.show(
            supportFragmentManager,
            ManuallyDisableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_DISABLE,
        )
    }

    private fun showEnableProtectionDialog(
        excludedAppInfo: TrackingProtectionAppInfo,
        position: Int,
    ) {
        val dialog = ManuallyEnableAppProtectionDialog.instance(excludedAppInfo, position)
        dialog.show(
            supportFragmentManager,
            ManuallyEnableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_ENABLE,
        )
    }

    private fun showFilterPopupMenu(anchor: View) {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_exclusion_list_filter_item_menu)
        val view = popupMenu.contentView
        val allItemView = view.findViewById<View>(R.id.allApps)
        val protectedItemView = view.findViewById<View>(R.id.protectedApps)
        val unprotectedItemView = view.findViewById<View>(R.id.unprotectedApps)

        popupMenu.apply {
            onMenuItemClicked(allItemView) { viewModel.applyAppsFilter(AppsFilter.ALL) }
            onMenuItemClicked(protectedItemView) { viewModel.applyAppsFilter(AppsFilter.PROTECTED_ONLY) }
            onMenuItemClicked(unprotectedItemView) { viewModel.applyAppsFilter(AppsFilter.UNPROTECTED_ONLY) }
        }
        popupMenu.showAnchoredToView(binding.root, anchor)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onAppProtectionEnabled(packageName: String) {
        viewModel.onAppProtectionEnabled(packageName)
    }

    override fun onDialogSkipped(position: Int) {
        adapter.notifyItemChanged(position)
    }

    override fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean,
    ) {
        viewModel.onAppProtectionDisabled(appName = appName, packageName = packageName, report = report)
    }

    private fun launchFeedback() {
        viewModel.launchFeedback()
    }

    private fun launchFaq() {
        globalActivityStarter.start(
            this,
            WebViewActivityWithParams(
                url = FAQ_WEBSITE,
                screenTitle = getString(string.atp_FAQActivityTitle),
            ),
        )
    }

    private fun getAppsFilterOrDefault(): AppsFilter {
        return intent.getSerializableExtra(KEY_FILTER_LIST) as AppsFilter? ?: AppsFilter.ALL
    }

    companion object {
        const val REPORT_ISSUES_ANNOTATION = "report_issues_link"
        const val LEARN_WHY_ANNOTATION = "learn_why_link"
        private const val KEY_FILTER_LIST = "KEY_FILTER_LIST"
        private const val FAQ_WEBSITE = "https://help.duckduckgo.com/duckduckgo-help-pages/p-app-tracking-protection/what-is-app-tracking-protection/"

        enum class AppsFilter {
            ALL,
            PROTECTED_ONLY,
            UNPROTECTED_ONLY,
        }

        internal fun intent(
            context: Context,
            filter: AppsFilter = AppsFilter.ALL,
        ): Intent {
            val intent = Intent(context, TrackingProtectionExclusionListActivity::class.java)
            intent.putExtra(KEY_FILTER_LIST, filter)
            return intent
        }
    }

    override fun onDefaultProtectionRestored() {
        viewModel.restoreProtectedApps()
        Snackbar.make(shimmerLayout, getString(R.string.atp_ExcludeAppsRestoreDefaultSnackbar), Snackbar.LENGTH_LONG)
            .show()
    }
}
