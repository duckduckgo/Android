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

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.content.res.Resources
import android.graphics.Typeface.BOLD
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import androidx.annotation.VisibleForTesting
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.model.dateOfLastDay
import com.duckduckgo.mobile.android.vpn.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import javax.inject.Inject

class DeviceShieldNotificationRepository @Inject constructor(
    private val resources: Resources,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
) {

    fun createDaily(): DeviceShieldNotification {
        val randomNumber = (0..3).shuffled().first()
        return createDailyNotification(randomNumber)
    }

    @VisibleForTesting
    fun createDailyNotification(randomNumber: Int): DeviceShieldNotification {

        val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastDay() })

        return when (randomNumber) {
            0 -> createTotalTrackersNotification(trackers)
            1 -> createTopTrackerCompanyNotification(trackers)
            2 -> createTopTrackerCompanyNumbersNotification(trackers)
            else -> createLastCompanyAttemptNotification(trackers)
        }
    }

    private fun createTotalTrackersNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldTrackers, trackers.size, trackers.size)
            val textToStyle = resources.getString(R.string.deviceShieldDailyTrackersNotification, totalTrackers)
            return DeviceShieldNotification(applyBoldSpanTo(listOf(totalTrackers), textToStyle))
        }
    }

    private fun createTopTrackerCompanyNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
            var topOffender = perCompany.values.first()
            perCompany.values.forEach {
                if (it.size > topOffender.size) {
                    topOffender = it
                }
            }

            val company = topOffender.first().trackerCompany.company
            val textToStyle = resources.getString(R.string.deviceShieldDailyTopCompanyNotification, company)

            return DeviceShieldNotification(applyBoldSpanTo(listOf(company), textToStyle))
        }
    }

    private fun createTopTrackerCompanyNumbersNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
            var topOffender = perCompany.values.first()
            perCompany.values.forEach {
                if (it.size > topOffender.size) {
                    topOffender = it
                }
            }
            val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldDailyCompanyBlocked, topOffender.size, topOffender.size)
            val company = topOffender.first().trackerCompany.company
            val textToStyle =
                resources.getString(R.string.deviceShieldDailyCompanyBlockedNotification, company, totalTrackers)
            DeviceShieldNotification(applyBoldSpanTo(listOf(company, totalTrackers), textToStyle))
        }
    }

    private fun createLastCompanyAttemptNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val lastCompany = trackers.first().trackerCompany.company
            val textToStyle = resources.getString(R.string.deviceShieldDailyLastCompanyBlockedNotification, lastCompany)
            DeviceShieldNotification(applyBoldSpanTo(listOf(lastCompany), textToStyle))
        }
    }

    fun createWeekly(): DeviceShieldNotification {
        val randomNumber = (0..1).shuffled().first()
        return createWeeklyNotification(randomNumber)
    }

    @VisibleForTesting
    fun createWeeklyNotification(randomNumber: Int): DeviceShieldNotification {
        val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastWeek() })
        return when (randomNumber) {
            0 -> createWeeklyReportNotification(trackers)
            else -> createWeeklyTopTrackerCompanyNotification(trackers)
        }
    }

    private fun createWeeklyReportNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }

            val totalCompanies = resources.getQuantityString(R.plurals.deviceShieldDailyCompany, perCompany.keys.size, perCompany.keys.size)
            val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldTrackers, trackers.size, trackers.size)
            val textToStyle = resources.getString(R.string.deviceShieldWeeklyCompanyTrackersBlockedNotification, totalCompanies, totalTrackers)

            return DeviceShieldNotification(applyBoldSpanTo(listOf(totalTrackers, totalCompanies), textToStyle))
        }
    }

    private fun createWeeklyTopTrackerCompanyNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        return if (trackers.isEmpty()) {
            DeviceShieldNotification(hidden = true)
        } else {
            val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
            var topOffender = perCompany.values.first()
            perCompany.values.forEach {
                if (it.size > topOffender.size) {
                    topOffender = it
                }
            }
            val company = topOffender.first().trackerCompany.company
            val textToStyle = resources.getString(R.string.deviceShieldWeeklyCompanyTeaserNotification, company)

            return DeviceShieldNotification(applyBoldSpanTo(listOf(company), textToStyle))
        }
    }

    private fun applyBoldSpanTo(textToStyle: List<String>, fullText: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(fullText)
        textToStyle.forEach {
            val index = fullText.indexOf(it)
            spannable.setSpan(StyleSpan(BOLD), index, index + it.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        }
        return spannable
    }

    data class DeviceShieldNotification(
        val text: SpannableStringBuilder = SpannableStringBuilder(),
        val silent: Boolean = false,
        val hidden: Boolean = false
    )
}