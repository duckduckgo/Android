/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.exclusion.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPAppExclusionListNoParams
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.R.layout
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpAppExclusionBinding
import com.duckduckgo.networkprotection.impl.exclusion.ui.AppExclusionListAdapter.ExclusionListListener
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProAppFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_EXCLUDED_APPS
import com.facebook.shimmer.ShimmerFrameLayout
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPAppExclusionListNoParams::class)
class NetpAppExclusionListActivity :
    DuckDuckGoActivity(),
    ManuallyDisableAppProtectionDialog.ManuallyDisableAppProtectionDialogListener,
    RestoreDefaultProtectionDialog.RestoreDefaultProtectionDialogListener {

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private lateinit var adapter: AppExclusionListAdapter

    private val binding: ActivityNetpAppExclusionBinding by viewBinding()
    private val viewModel: NetpAppExclusionListViewModel by bindViewModel()
    private val shimmerLayout: ShimmerFrameLayout by lazy { binding.netpAppExclusionListSkeleton }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        bindViews()
        observeViewModel()

        viewModel.applyAppsFilter(AppsFilter.ALL)
        lifecycle.addObserver(viewModel)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_netp_exclusion_list_activity, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val restoreDefault = menu.findItem(R.id.netp_exclusion_menu_restore)
        // onPrepareOptionsMenu is called when overflow menu is being displayed, that's why this can be an imperative call
        restoreDefault?.isEnabled = viewModel.canRestoreDefaults()

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.netp_exclusion_menu_restore -> {
                val dialog = RestoreDefaultProtectionDialog.instance()
                dialog.show(supportFragmentManager, RestoreDefaultProtectionDialog.TAG_RESTORE_DEFAULT_PROTECTION)
                true
            }

            R.id.netp_exclusion_menu_report -> {
                viewModel.launchFeedback(); true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onAppProtectionDisabled(
        appName: String,
        packageName: String,
        report: Boolean,
    ) {
        viewModel.onAppProtectionDisabled(appName = appName, packageName = packageName, report = report)
    }

    override fun onDefaultProtectionRestored() {
        viewModel.restoreProtectedApps()
    }

    private fun bindViews() {
        shimmerLayout.startShimmer()
        setupRecycler()
    }

    private fun observeViewModel() {
        viewModel.initialize()

        lifecycleScope.launch {
            viewModel.getApps()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun setupRecycler() {
        adapter = AppExclusionListAdapter(
            object : ExclusionListListener {
                override fun onAppProtectionChanged(
                    app: NetpExclusionListApp,
                    enabled: Boolean,
                    position: Int,
                ) {
                    viewModel.onAppProtectionChanged(app, enabled)
                }

                override fun onFilterClick(anchorView: View) {
                    showFilterPopupMenu(anchorView)
                }

                override fun onSystemAppCategoryStateChanged(
                    category: NetpExclusionListSystemAppCategory,
                    enabled: Boolean,
                    position: Int,
                ) {
                    viewModel.onSystemAppCategoryStateChanged(category, enabled)
                }
            },
        )

        binding.netpAppExclusionListRecycler.adapter = adapter
    }

    private fun showFilterPopupMenu(anchor: View) {
        val popupMenu = PopupMenu(layoutInflater, layout.popup_exclusion_list_filter)
        val view = popupMenu.contentView
        val allItemView = view.findViewById<View>(R.id.exclusion_list_filter_all)
        val protectedItemView = view.findViewById<View>(R.id.exclusion_list_filter_protected)
        val unprotectedItemView = view.findViewById<View>(R.id.exclusion_list_filter_unprotected)

        popupMenu.apply {
            onMenuItemClicked(allItemView) { viewModel.applyAppsFilter(AppsFilter.ALL) }
            onMenuItemClicked(protectedItemView) { viewModel.applyAppsFilter(AppsFilter.PROTECTED_ONLY) }
            onMenuItemClicked(unprotectedItemView) { viewModel.applyAppsFilter(AppsFilter.UNPROTECTED_ONLY) }
        }
        popupMenu.showAnchoredToView(binding.root, anchor)
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
                command.forApp,
            )

            is Command.ShowIssueReportingPage -> globalActivityStarter.start(this, command.params)
            is Command.ShowUnifiedPproAppFeedback -> globalActivityStarter.start(
                this,
                PrivacyProAppFeedbackScreenWithParams(
                    appName = command.appName,
                    appPackageName = command.appPackageName,
                ),
            )

            is Command.ShowUnifiedPproFeedback -> globalActivityStarter.start(
                this,
                PrivacyProFeedbackScreenWithParams(feedbackSource = VPN_EXCLUDED_APPS),
            )

            is Command.ShowSystemAppsExclusionWarning -> showSystemAppsWarning(command.category)
            else -> { /* noop */
            }
        }
    }

    private fun showSystemAppsWarning(category: NetpExclusionListSystemAppCategory) {
        TextAlertDialogBuilder(this@NetpAppExclusionListActivity)
            .setTitle(R.string.netpExclusionListWarningTitle)
            .setMessage(R.string.netpExclusionListWarningMessage)
            .setPositiveButton(R.string.netpExclusionListWarningActionPositive)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onSystemAppCategoryStateChanged(category, false)
                    }
                },
            )
            .show()
    }

    private fun restartVpn() {
        // we use the app coroutine scope to ensure this call outlives the Activity
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (vpnFeaturesRegistry.isFeatureRunning(NetPVpnFeature.NETP_VPN)) {
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            }
        }
    }

    private fun showDisableProtectionDialog(app: NetpExclusionListApp) {
        val dialog = ManuallyDisableAppProtectionDialog.instance(app)
        dialog.show(
            supportFragmentManager,
            ManuallyDisableAppProtectionDialog.TAG_MANUALLY_EXCLUDE_APPS_DISABLE,
        )
    }

    companion object {
        enum class AppsFilter {
            ALL,
            PROTECTED_ONLY,
            UNPROTECTED_ONLY,
        }
    }
}
