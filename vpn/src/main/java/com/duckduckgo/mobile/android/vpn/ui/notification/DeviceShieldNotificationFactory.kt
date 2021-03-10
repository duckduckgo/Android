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
import android.text.SpannableStringBuilder
import androidx.annotation.VisibleForTesting
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.model.dateOfLastDay
import com.duckduckgo.mobile.android.vpn.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import javax.inject.Inject

class DeviceShieldNotificationFactory @Inject constructor(
    private val resources: Resources,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
) {

    fun createEnabledDeviceShieldNotification(): DeviceShieldNotification {
        val title = SpannableStringBuilder(resources.getString(R.string.deviceShieldOnInitialNotification))
        return DeviceShieldNotification(title)
    }

    fun createTrackersCountDeviceShieldNotification(trackersBlocked: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val trackerCompaniesTotal = trackersBlocked.groupBy { it.trackerCompany.trackerCompanyId }.size
        val trackerCompanies = trackersBlocked.distinctBy { it.trackerCompany.trackerCompanyId }

        val title = when {
            trackersBlocked.isEmpty() -> SpannableStringBuilder(resources.getString(R.string.deviceShieldOnNoTrackersNotificationHeader))
            else -> {
                val trackerText = resources.getQuantityString(R.plurals.deviceShieldOnNotificationTrackers, trackersBlocked.size, trackersBlocked.size)
                val textToStyle = trackerText.plus(resources.getString(R.string.deviceShieldOnNoTrackersNotificationMessageTimeSuffix))
                textToStyle.applyBoldSpanTo(listOf(trackerText))
            }
        }

        val message = if (trackersBlocked.isEmpty()) {
            SpannableStringBuilder(resources.getString(R.string.deviceShieldOnNoTrackersNotificationMessage))
        } else {
            when (trackerCompaniesTotal) {
                1 -> {
                    val nonStyledText = resources.getString(R.string.deviceShieldNotificationOneCompanyBlocked, trackerCompanies.first().trackerCompany.company)
                    nonStyledText.applyBoldSpanTo(listOf(trackerCompanies.first().trackerCompany.company))
                }
                2 -> {
                    val nonStyledText = resources.getString(
                        R.string.deviceShieldNotificationTwoCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company
                    )
                    nonStyledText.applyBoldSpanTo(
                        listOf(
                            trackerCompanies.first().trackerCompany.company,
                            trackerCompanies[1].trackerCompany.company
                        )
                    )
                }
                3 -> {
                    val nonStyledText = resources.getString(
                        R.string.deviceShieldNotificationThreeCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company,
                        trackerCompanies[2].trackerCompany.company
                    )
                    nonStyledText.applyBoldSpanTo(
                        listOf(
                            trackerCompanies.first().trackerCompany.company,
                            trackerCompanies[1].trackerCompany.company,
                            trackerCompanies[2].trackerCompany.company
                        )
                    )
                }
                else -> {
                    val nonStyledText = resources.getString(
                        R.string.deviceShieldNotificationFourCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company,
                        trackerCompaniesTotal - 2
                    )
                    nonStyledText.applyBoldSpanTo(
                        listOf(
                            trackerCompanies.first().trackerCompany.company,
                            trackerCompanies[1].trackerCompany.company
                        )
                    )
                }
            }
        }

        return DeviceShieldNotification(title = title, message = message)
    }

    fun createDailyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..3).shuffled().first()
        return createDailyDeviceShieldNotification(randomNumber)
    }

    @VisibleForTesting
    fun createDailyDeviceShieldNotification(dailyNotificationType: Int): DeviceShieldNotification {

        val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastDay() })

        if (trackers.isEmpty()) {
            return DeviceShieldNotification(hidden = true)
        }
        return when (dailyNotificationType) {
            0 -> createDailyTotalTrackersNotification(trackers)
            1 -> createDailyTopTrackerCompanyNotification(trackers)
            2 -> createDailyTopTrackerCompanyNumbersNotification(trackers)
            else -> createDailyLastCompanyAttemptNotification(trackers)
        }
    }

    private fun createDailyTotalTrackersNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldTrackers, trackers.size, trackers.size)
        val textToStyle = resources.getString(R.string.deviceShieldDailyTrackersNotification, totalTrackers)
        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(totalTrackers)))
    }

    private fun createDailyTopTrackerCompanyNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
        var topOffender = perCompany.values.first()
        perCompany.values.forEach {
            if (it.size > topOffender.size) {
                topOffender = it
            }
        }

        val company = topOffender.first().trackerCompany.company
        val textToStyle = resources.getString(R.string.deviceShieldDailyTopCompanyNotification, company)

        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(company)))

    }

    private fun createDailyTopTrackerCompanyNumbersNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
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
        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(company, totalTrackers)))
    }

    private fun createDailyLastCompanyAttemptNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val lastCompany = trackers.first().trackerCompany.company
        val textToStyle = resources.getString(R.string.deviceShieldDailyLastCompanyBlockedNotification, lastCompany)
        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(lastCompany)))
    }

    fun createWeeklyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..1).shuffled().first()
        return createWeeklyDeviceShieldNotification(randomNumber)
    }

    @VisibleForTesting
    fun createWeeklyDeviceShieldNotification(randomNumber: Int): DeviceShieldNotification {
        val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastWeek() })
        if (trackers.isEmpty()) {
            return DeviceShieldNotification(hidden = true)
        }

        return when (randomNumber) {
            0 -> createWeeklyReportNotification(trackers)
            else -> createWeeklyTopTrackerCompanyNotification(trackers)
        }
    }

    private fun createWeeklyReportNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
        val totalCompanies = resources.getQuantityString(R.plurals.deviceShieldDailyCompany, perCompany.keys.size, perCompany.keys.size)
        val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldTrackers, trackers.size, trackers.size)
        val textToStyle = resources.getString(R.string.deviceShieldWeeklyCompanyTrackersBlockedNotification, totalCompanies, totalTrackers)

        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(totalTrackers, totalCompanies)))
    }

    private fun createWeeklyTopTrackerCompanyNotification(trackers: List<VpnTrackerAndCompany>): DeviceShieldNotification {
        val perCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
        var topOffender = perCompany.values.first()
        perCompany.values.forEach {
            if (it.size > topOffender.size) {
                topOffender = it
            }
        }
        val company = topOffender.first().trackerCompany.company
        val textToStyle = resources.getString(R.string.deviceShieldWeeklyCompanyTeaserNotification, company)

        return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(company)))
    }

    data class DeviceShieldNotification(
        val title: SpannableStringBuilder = SpannableStringBuilder(),
        val message: SpannableStringBuilder = SpannableStringBuilder(),
        val silent: Boolean = false,
        val hidden: Boolean = false
    )
}
