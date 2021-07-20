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
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.rightDrawable
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.DeviceShieldExcludedApps
import com.duckduckgo.mobile.android.vpn.apps.ExcludedAppsViewModel
import com.duckduckgo.mobile.android.vpn.apps.ViewState
import com.duckduckgo.mobile.android.vpn.apps.VpnExcludedInstalledAppInfo
import com.facebook.shimmer.ShimmerFrameLayout
import dagger.android.AndroidInjection
import dummy.ui.VpnControllerActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class DeviceShieldExclusionListActivity : AppCompatActivity(R.layout.activity_device_shield_exclusion_list) {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ExcludedAppsViewModel by bindViewModel()

    @Inject
    lateinit var deviceShieldExcludedApps: DeviceShieldExcludedApps

    private val appsContainer by lazy { findViewById<View>(R.id.exclusionListAppsContainer) }
    private val appsWithIssuesLabel by lazy { findViewById<View>(R.id.exclusionListAppsWithIssuesLabel) }
    private val appsWithIssuesContainer by lazy { findViewById<LinearLayout>(R.id.exclusionListAppsWithIssuesContainer) }
    private val appsWithIssuesHeader by lazy { findViewById<TextView>(R.id.exclusionListAppsWithIssuesHeader) }
    private val appsWithIssuesReport by lazy { findViewById<View>(R.id.exclusionListAppsWithIssuesReport) }
    private val otherAppsLabel by lazy { findViewById<View>(R.id.exclusionListOtherAppsLabel) }
    private val otherAppsContainer by lazy { findViewById<LinearLayout>(R.id.exclusionListOtherAppsContainer) }
    private val otherAppsHeader by lazy { findViewById<TextView>(R.id.exclusionListOtherAppsHeader) }
    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.deviceShieldExclusionAppListSkeleton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        shimmerLayout.startShimmer()

        appsWithIssuesReport.setOnClickListener {
            launchFeedback()
        }

        lifecycleScope.launch {
            viewModel.getExcludedApps()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderExcludedApps(it) }
        }
    }

    private fun renderExcludedApps(viewState: ViewState) {
        renderAppsWithKnownIssues(viewState)
        renderOtherApps(viewState)
        appsContainer.show()
        shimmerLayout.gone()
    }

    private fun renderAppsWithKnownIssues(viewState: ViewState) {
        appsWithIssuesHeader.text = String.format(getString(R.string.atp_ExcludedAppsKnownIssuesHeader), viewState.appsWithIssues.size)
        appsWithIssuesHeader.setOnClickListener {
            if (appsWithIssuesContainer.childCount > 0) {
                removeExcludedApps(appsWithIssuesHeader, appsWithIssuesContainer)
                addExcludedApps(otherAppsHeader, viewState.otherApps, otherAppsContainer)
                appsWithIssuesReport.gone()
                appsWithIssuesLabel.gone()
                otherAppsLabel.show()
            } else {
                addExcludedApps(appsWithIssuesHeader, viewState.appsWithIssues, appsWithIssuesContainer)
                removeExcludedApps(otherAppsHeader, otherAppsContainer)
                appsWithIssuesReport.show()
                appsWithIssuesLabel.show()
                otherAppsLabel.gone()
            }
        }
    }

    private fun renderOtherApps(viewState: ViewState) {
        viewState.appsWithIssues.forEach {
            appsWithIssuesContainer.addView(createAppView(it))
        }

        otherAppsHeader.text = String.format(getString(R.string.atp_ExcludedOtherAppsHeader), viewState.otherApps.size)
        otherAppsHeader.setOnClickListener {
            if (otherAppsContainer.childCount > 1) {
                removeExcludedApps(otherAppsHeader, otherAppsContainer)
                addExcludedApps(appsWithIssuesHeader, viewState.appsWithIssues, appsWithIssuesContainer)
                otherAppsLabel.gone()
                appsWithIssuesReport.show()
                appsWithIssuesLabel.show()
            } else {
                addExcludedApps(otherAppsHeader, viewState.otherApps, otherAppsContainer)
                removeExcludedApps(appsWithIssuesHeader, appsWithIssuesContainer)
                otherAppsLabel.show()
                appsWithIssuesReport.gone()
                appsWithIssuesLabel.gone()
            }
        }
    }

    private fun addExcludedApps(header: TextView, apps: List<VpnExcludedInstalledAppInfo>, container: LinearLayout) {
        apps.forEach {
            container.addView(createAppView(it))
        }
        header.rightDrawable(R.drawable.ic_collapse)
    }

    private fun removeExcludedApps(header: TextView, container: LinearLayout) {
        container.removeAllViews()
        header.rightDrawable(R.drawable.ic_expand)
    }

    private fun createAppView(appInfo: VpnExcludedInstalledAppInfo): View {
        return DeviceShieldExcludedAppView(this@DeviceShieldExclusionListActivity).apply {
            appName = "${appInfo.name}"
            appIcon = packageManager.safeGetApplicationIcon(appInfo.packageName)
            isShieldEnabled = appInfo.isExcludedFromVpn
            tag = appInfo.packageName
            shieldListener = object : DeviceShieldExcludedAppView.ShieldListener {
                override fun onAppShieldChanged(view: View, enabled: Boolean) {
                    if (enabled) {
                        deviceShieldExcludedApps.addToExclusionList((view.tag as String?).orEmpty())
                    } else {
                        deviceShieldExcludedApps.removeFromExclusionList((view.tag as String?).orEmpty())
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun launchFeedback() {
        startActivity(Intent(Intent.ACTION_VIEW, VpnControllerActivity.FEEDBACK_URL))
    }

    private fun PackageManager.safeGetApplicationIcon(packageName: String): Drawable? {
        return runCatching {
            getApplicationIcon(packageName)
        }.getOrNull()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldExclusionListActivity::class.java)
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }
}
