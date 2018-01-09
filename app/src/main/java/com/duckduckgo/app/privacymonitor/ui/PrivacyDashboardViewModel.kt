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
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.*
import com.duckduckgo.app.privacymonitor.renderer.smallIcon
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore

@SuppressLint("StaticFieldLeak")
class PrivacyDashboardViewModel(private val applicationContext: Context,
                                private val settingsStore: PrivacySettingsStore) : ViewModel() {

    data class ViewState(
            val domain: String,
            val heading: String,
            val privacyGrade: PrivacyGrade,
            val httpsStatus: HttpsStatus,
            val networkCount: Int,
            val allTrackersBlocked: Boolean,
            val practices: TermsOfService.Practices,
            val toggleEnabled: Boolean
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
                privacyGrade = PrivacyGrade.UNKNOWN,
                httpsStatus = HttpsStatus.SECURE,
                networkCount = 0,
                allTrackersBlocked = true,
                practices = TermsOfService.Practices.UNKNOWN,
                toggleEnabled = settingsStore.privacyOn
        )
    }

    private fun updatePrivacyMonitor(monitor: PrivacyMonitor) {
        viewState.value = viewState.value?.copy(
                domain = monitor.uri?.host ?: "",
                heading = headingText(),
                privacyGrade = monitor.improvedGrade,
                httpsStatus = monitor.https,
                networkCount = monitor.networkCount,
                allTrackersBlocked = monitor.allTrackersBlocked,
                practices = monitor.termsOfService.practices
        )
    }

    fun onPrivacyToggled(enabled: Boolean) {
        if (enabled != viewState.value?.toggleEnabled) {
            settingsStore.privacyOn = enabled
            viewState.value = viewState.value?.copy(
                    heading = headingText(),
                    toggleEnabled = enabled
            )
        }
    }

    private fun headingText(): String {
        val monitor = monitor
        if (monitor != null) {
            val before = monitor.grade
            val after = monitor.improvedGrade
            if (before != after) {
                return applicationContext.getString(R.string.privacyProtectionUpgraded, before.smallIcon(), after.smallIcon())
            }
        }
        val resource = if (settingsStore.privacyOn) R.string.privacyProtectionEnabled else R.string.privacyProtectionDisabled
        return applicationContext.getString(resource)
    }
}