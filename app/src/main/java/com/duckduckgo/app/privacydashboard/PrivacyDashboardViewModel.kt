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

package com.duckduckgo.app.privacydashboard

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor

@SuppressLint("StaticFieldLeak")
class PrivacyDashboardViewModel(
        private val context: Context) : ViewModel() {

    data class ViewState(
            val domain: String,
            val httpsIcon: Int,
            val httpsText: String,
            val networksText: String,
            val networksIcon: Int = R.drawable.dashboard_networks_blocked,
            val majorNetworksText: String,
            val majorNetworksIcon: Int = R.drawable.dashboard_major_networks_blocked
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    init {
        resetPrivacyMonitor()
    }

    fun onPrivacyMonitorChanged(monitor: PrivacyMonitor?) {
        if (monitor == null) {
            resetPrivacyMonitor()
        } else {
            updatePrivacyMonitor(monitor)
        }
    }

    private fun resetPrivacyMonitor() {
        viewState.value = ViewState(
                domain = "",
                httpsIcon = httpsIcon(HttpsStatus.SECURE),
                httpsText = httpsText(HttpsStatus.SECURE),
                networksText = networksText(0),
                majorNetworksText = majorNetworksText(0)
        )
    }

    private fun updatePrivacyMonitor(monitor: PrivacyMonitor) {
        viewState.value = viewState.value?.copy(
                domain = monitor.uri?.host ?: "",
                httpsIcon = httpsIcon(monitor.https),
                httpsText = httpsText(monitor.https),
                networksText = networksText(monitor.networkCount),
                majorNetworksText = majorNetworksText(monitor.majorNetworkCount)
        )
    }

    private fun httpsText(status: HttpsStatus): String = when (status) {
        HttpsStatus.NONE -> context.getString(R.string.httpsBad)
        HttpsStatus.MIXED -> context.getString(R.string.httpsMixed)
        HttpsStatus.SECURE -> context.getString(R.string.httpsGood)
    }

    private fun httpsIcon(status: HttpsStatus): Int = when (status) {
        HttpsStatus.NONE -> R.drawable.dashboard_https_bad
        HttpsStatus.MIXED -> R.drawable.dashboard_https_neutral
        HttpsStatus.SECURE -> R.drawable.dashboard_https_good
    }

    private fun networksText(trackerNetworkCount: Int): String =
            context.getString(R.string.networksBlocked, trackerNetworkCount.toString())


    private fun majorNetworksText(trackerNetworkCount: Int): String =
            context.getString(R.string.majorNetworksBlocked, trackerNetworkCount.toString())
}