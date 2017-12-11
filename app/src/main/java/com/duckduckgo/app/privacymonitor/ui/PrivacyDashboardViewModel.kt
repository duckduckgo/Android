/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.ui

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore

@SuppressLint("StaticFieldLeak")
class PrivacyDashboardViewModel(private val context: Context,
                                private val settingsStore: PrivacySettingsStore) : ViewModel() {

    data class ViewState(
            val domain: String,
            val heading: String,
            val httpsIcon: Int,
            val httpsText: String,
            val networksText: String,
            val networksIcon: Int = R.drawable.dashboard_networks_good,
            val majorNetworksText: String,
            val majorNetworksIcon: Int = R.drawable.dashboard_major_networks_good,
            val toggleEnabled: Boolean,
            val toggleText: String,
            val toggleBackgroundColor: Int = R.color.midGreen
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    private val privacyInitiallyOn = settingsStore.privacyOn
    private var monitor: PrivacyMonitor? = null

    init {
        resetViewState()
    }

    val shouldReloadPage: Boolean
        get() = privacyInitiallyOn != settingsStore.privacyOn

    fun onPrivacyMonitorChanged(monitor: PrivacyMonitor?) {
        this.monitor = monitor
        if (monitor == null) {
            resetViewState()
        } else {
            updatePrivacyMonitor(monitor)
        }
    }

    private fun resetViewState() {
        viewState.value = ViewState(
                domain = "",
                heading = headingText(),
                httpsIcon = httpsIcon(HttpsStatus.SECURE),
                httpsText = httpsText(HttpsStatus.SECURE),
                networksIcon = R.drawable.dashboard_networks_good,
                networksText = networksText(),
                majorNetworksText = majorNetworksText(),
                majorNetworksIcon = R.drawable.dashboard_networks_good,
                toggleEnabled = settingsStore.privacyOn,
                toggleText = toggleText(),
                toggleBackgroundColor = toggleBackgroundColor()
        )
    }

    private fun updatePrivacyMonitor(monitor: PrivacyMonitor) {
        viewState.value = viewState.value?.copy(
                domain = monitor.uri?.host ?: "",
                httpsIcon = httpsIcon(monitor.https),
                httpsText = httpsText(monitor.https),
                networksIcon = networksIcon(monitor.allTrackersBlocked, monitor.networkCount),
                networksText = networksText(monitor.allTrackersBlocked, monitor.networkCount),
                majorNetworksIcon = majorNetworksIcon(monitor.allTrackersBlocked, monitor.majorNetworkCount),
                majorNetworksText = majorNetworksText(monitor.allTrackersBlocked, monitor.majorNetworkCount)
        )
    }

    fun onPrivacyToggled(enabled: Boolean) {
        if (enabled != viewState.value?.toggleEnabled) {
            settingsStore.privacyOn = enabled
            viewState.value = viewState.value?.copy(
                    heading = headingText(),
                    toggleEnabled = enabled,
                    toggleText = toggleText(),
                    toggleBackgroundColor = toggleBackgroundColor()
            )
        }
    }

    private fun headingText(): String {
        val resource = if (settingsStore.privacyOn) R.string.privacyProtectionEnabled else R.string.privacyProtectionDisabled
        return context.getString(resource)
    }

    private fun httpsIcon(status: HttpsStatus): Int = when (status) {
        HttpsStatus.NONE -> R.drawable.dashboard_https_bad
        HttpsStatus.MIXED -> R.drawable.dashboard_https_neutral
        HttpsStatus.SECURE -> R.drawable.dashboard_https_good
    }

    private fun httpsText(status: HttpsStatus): String = when (status) {
        HttpsStatus.NONE -> context.getString(R.string.httpsBad)
        HttpsStatus.MIXED -> context.getString(R.string.httpsMixed)
        HttpsStatus.SECURE -> context.getString(R.string.httpsGood)
    }

    private fun networksIcon(allBlocked: Boolean = true, networksCount: Int = 0): Int {
        val isGood = allBlocked || networksCount == 0
        return if (isGood) R.drawable.dashboard_networks_good else R.drawable.dashboard_networks_bad
    }

    private fun networksText(allBlocked: Boolean = true, networkCount: Int = 0): String {
        val resource = if (allBlocked) R.string.networksBlocked else R.string.networksFound
        return context.getString(resource, networkCount.toString())
    }

    private fun majorNetworksIcon(allBlocked: Boolean = true, majorNetworksCount: Int = 0): Int {
        val isGood = allBlocked || majorNetworksCount == 0
        return if (isGood) R.drawable.dashboard_major_networks_good else R.drawable.dashboard_major_networks_bad
    }

    private fun majorNetworksText(allBlocked: Boolean = true, networkCount: Int = 0): String {
        val resource = if (allBlocked) R.string.majorNetworksBlocked else R.string.majorNetworksFound
        return context.getString(resource, networkCount.toString())
    }

    private fun toggleText(): String {
        val resource = if (settingsStore.privacyOn) R.string.privacyProtectionToggleOn else R.string.privacyProtectionToggleOff
        return context.getString(resource)
    }

    private fun toggleBackgroundColor(): Int {
        return if (settingsStore.privacyOn) R.color.midGreen else R.color.warmerGrey
    }
}