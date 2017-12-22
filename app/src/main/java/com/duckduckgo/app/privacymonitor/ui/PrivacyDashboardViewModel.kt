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
import android.support.annotation.DrawableRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.HttpsStatus
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.model.grade
import com.duckduckgo.app.privacymonitor.model.improvedGrade
import com.duckduckgo.app.privacymonitor.store.PrivacySettingsStore

@SuppressLint("StaticFieldLeak")
class PrivacyDashboardViewModel(private val applicationContext: Context,
                                private val settingsStore: PrivacySettingsStore) : ViewModel() {

    data class ViewState(
            @DrawableRes val privacyBanner: Int,
            val domain: String,
            val heading: String,
            @DrawableRes val httpsIcon: Int,
            val httpsText: String,
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
                privacyBanner = R.drawable.privacygrade_banner_unknown,
                domain = "",
                heading = headingText(),
                httpsIcon = httpsIcon(HttpsStatus.SECURE),
                httpsText = httpsText(HttpsStatus.SECURE),
                networkCount = 0,
                allTrackersBlocked = true,
                practices = TermsOfService.Practices.UNKNOWN,
                toggleEnabled = settingsStore.privacyOn
        )
    }

    private fun updatePrivacyMonitor(monitor: PrivacyMonitor) {
        viewState.value = viewState.value?.copy(
                privacyBanner = privacyBanner(monitor.improvedGrade),
                domain = monitor.uri?.host ?: "",
                heading = headingText(),
                httpsIcon = httpsIcon(monitor.https),
                httpsText = httpsText(monitor.https),
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
                    privacyBanner = privacyBanner(monitor?.improvedGrade),
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
                return applicationContext.getString(R.string.privacyProtectionUpgraded, privacyGradeIcon(before), privacyGradeIcon(after))
            }
        }
        val resource = if (settingsStore.privacyOn) R.string.privacyProtectionEnabled else R.string.privacyProtectionDisabled
        return applicationContext.getString(resource)
    }

    private fun privacyBanner(grade: PrivacyGrade?): Int {
        if (settingsStore.privacyOn) {
            return privacyBannerOn(grade)
        }
        return privacyBannerOff(grade)
    }

    @DrawableRes
    private fun privacyBannerOn(grade: PrivacyGrade?): Int {
        return when (grade) {
            PrivacyGrade.A -> R.drawable.privacygrade_banner_a_on
            PrivacyGrade.B -> R.drawable.privacygrade_banner_b_on
            PrivacyGrade.C -> R.drawable.privacygrade_banner_c_on
            PrivacyGrade.D -> R.drawable.privacygrade_banner_d_on
            else -> R.drawable.privacygrade_banner_unknown
        }
    }

    @DrawableRes
    private fun privacyBannerOff(grade: PrivacyGrade?): Int {
        return when (grade) {
            PrivacyGrade.A -> R.drawable.privacygrade_banner_a_off
            PrivacyGrade.B -> R.drawable.privacygrade_banner_b_off
            PrivacyGrade.C -> R.drawable.privacygrade_banner_c_off
            PrivacyGrade.D -> R.drawable.privacygrade_banner_d_off
            else -> R.drawable.privacygrade_banner_unknown
        }
    }

    @DrawableRes
    private fun privacyGradeIcon(grade: PrivacyGrade): Int {
        return when (grade) {
            PrivacyGrade.A -> R.drawable.privacygrade_icon_small_a
            PrivacyGrade.B -> R.drawable.privacygrade_icon_small_b
            PrivacyGrade.C -> R.drawable.privacygrade_icon_small_c
            PrivacyGrade.D -> R.drawable.privacygrade_icon_small_d
        }
    }

    @DrawableRes
    private fun httpsIcon(status: HttpsStatus): Int = when (status) {
        HttpsStatus.NONE -> R.drawable.dashboard_https_bad
        HttpsStatus.MIXED -> R.drawable.dashboard_https_neutral
        HttpsStatus.SECURE -> R.drawable.dashboard_https_good
    }

    private fun httpsText(status: HttpsStatus): String = when (status) {
        HttpsStatus.NONE -> applicationContext.getString(R.string.httpsBad)
        HttpsStatus.MIXED -> applicationContext.getString(R.string.httpsMixed)
        HttpsStatus.SECURE -> applicationContext.getString(R.string.httpsGood)
    }
}