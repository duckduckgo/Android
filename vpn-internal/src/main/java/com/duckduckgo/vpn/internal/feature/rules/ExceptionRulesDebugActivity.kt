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

package com.duckduckgo.vpn.internal.feature.rules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRule
import com.duckduckgo.vpn.internal.databinding.ActivityExceptionRulesDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ExceptionRulesDebugActivity : DuckDuckGoActivity(), RuleTrackerView.RuleTrackerListener {

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var exclusionRulesRepository: ExclusionRulesRepository

    private val binding: ActivityExceptionRulesDebugBinding by viewBinding()

    private val refreshTickerJob = ConflatedJob()
    private var refreshTickerChannel = MutableStateFlow(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.appRule.isVisible = false
        binding.progress.isVisible = true

        vpnDatabase.vpnAppTrackerBlockingDao()
            .getTrackerExceptionRulesFlow()
            .combine(refreshTickerChannel.asStateFlow()) { trackers, _ -> trackers }
            .map { rules ->
                rules to getAppTrackers()
            }
            .onStart { startRefreshTicker() }
            .flowOn(Dispatchers.IO)
            .onEach { it ->
                val (rules, appTrackers) = it

                // clean up the screen
                binding.appRule.removeAllViews()

                // re-build the screen
                appTrackers.forEach { installAppTracker ->
                    val appView = RuleAppView(this).apply {
                        appIcon = packageManager.safeGetApplicationIcon(installAppTracker.packageName)
                        appName = installAppTracker.name.orEmpty()
                    }
                    binding.appRule.addView(appView)
                    installAppTracker.blockedDomains.forEach { domain ->
                        val domainView = RuleTrackerView(this).apply {
                            this.domain = domain
                            this.isChecked = rules.containsRule(installAppTracker.packageName, domain)
                            this.ruleTrackerListener = this@ExceptionRulesDebugActivity
                            tag = "${installAppTracker.packageName}_$domain"
                        }
                        appView.addTrackerView(domainView)
                    }
                }
                binding.appRule.isVisible = true
                binding.progress.isVisible = false

            }
            .flowOn(Dispatchers.Main)
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshTickerJob.cancel()
    }

    private fun getAppTrackers(): List<InstalledAppTrackers> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .map {
                val blockedTrackers = vpnDatabase.vpnTrackerDao().getTrackersForApp(it.packageName)
                    .map { tracker -> tracker.domain }
                    .toSortedSet() // dedup
                InstalledAppTrackers(it.packageName, it.name, blockedTrackers)
            }
            .filter { it.blockedDomains.isNotEmpty() }
            .sortedBy { it.name }
            .toList()
    }

    private fun List<AppTrackerExceptionRule>.containsRule(
        packageName: String,
        domain: String
    ): Boolean {
        forEach { exclusionRule ->
            if (exclusionRule.rule == domain && exclusionRule.packageNames.contains(packageName)) return true
        }

        return false
    }

    private fun PackageManager.safeGetApplicationIcon(packageName: String): Drawable? {
        return runCatching {
            getApplicationIcon(packageName)
        }.getOrNull()
    }

    private fun startRefreshTicker() {
        refreshTickerJob += lifecycleScope.launch {
            while (isActive) {
                refreshTickerChannel.emit(System.currentTimeMillis())
                delay(TimeUnit.SECONDS.toMillis(5))
            }
        }
    }

    override fun onTrackerClicked(
        view: View,
        enabled: Boolean
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tag = (view.tag as String?).orEmpty()
            val (appPackageName, domain) = tag.split("_")
            Timber.v("$appPackageName / $domain enabled: $enabled")
            if (enabled) {
                sendBroadcast(ExceptionRulesDebugReceiver.ruleIntent(appPackageName, domain))
            } else {
                exclusionRulesRepository.removeRule(appPackageName, domain)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ExceptionRulesDebugActivity::class.java)
        }
    }
}

private data class InstalledApp(
    val packageName: String,
    val name: String? = null,
)

private data class InstalledAppTrackers(
    val packageName: String,
    val name: String? = null,
    val blockedDomains: Set<String>
)
