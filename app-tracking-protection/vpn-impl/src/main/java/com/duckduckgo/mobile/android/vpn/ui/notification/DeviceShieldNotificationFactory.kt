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
import androidx.core.text.HtmlCompat
import com.duckduckgo.common.utils.formatters.time.model.dateOfLastDay
import com.duckduckgo.common.utils.formatters.time.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import kotlinx.coroutines.flow.firstOrNull
import logcat.logcat
import javax.inject.Inject

class DeviceShieldNotificationFactory @Inject constructor(
    private val resources: Resources,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
) {

    @VisibleForTesting
    val dailyNotificationFactory = DeviceShieldDailyNotificationFactory()

    @VisibleForTesting
    val weeklyNotificationFactory = DeviceShieldWeeklyNotificationFactory()

    suspend fun createDailyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..3).shuffled().first()
        return dailyNotificationFactory.createDailyDeviceShieldNotification(randomNumber)
    }

    suspend fun createWeeklyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..1).shuffled().first()
        return weeklyNotificationFactory.createWeeklyDeviceShieldNotification(randomNumber)
    }

    private fun getNumberOfAppsContainingTopOffender(
        trackers: List<VpnTracker>,
        topOffender: VpnTracker,
    ): Map<TrackingApp, List<VpnTracker>> {
        return trackers.filter { it.trackerCompanyId == topOffender.trackerCompanyId }.groupBy { it.trackingApp }
    }

    inner class DeviceShieldDailyNotificationFactory {

        suspend fun createDailyDeviceShieldNotification(dailyNotificationType: Int): DeviceShieldNotification {
            val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastDay() })
            val trackerCount =
                appTrackerBlockingStatsRepository.getBlockedTrackersCountBetween({ dateOfLastDay() }).firstOrNull() ?: trackers.sumOf { it.count }
            logcat {
                "createDailyDeviceShieldNotification. $trackerCount total trackers in the last day, number of trackers returned is " +
                    "${trackers.sumOf { it.count }}. Notification type: $dailyNotificationType"
            }

            if (trackers.isEmpty()) {
                return DeviceShieldNotification(hidden = true)
            }

            val apps = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.sumOf { t -> t.count } }
            val firstAppName = apps.firstOrNull()?.first?.appDisplayName ?: ""

            return when (dailyNotificationType) {
                0 -> createDailyTotalTrackersNotification(trackerCount, apps.size, firstAppName)
                1 -> createDailyTopTrackerCompanyNotification(trackers)
                2 -> createDailyNotificationTopAppsContainingTrackers(apps)
                else -> createDailyLastCompanyAttemptNotification(trackers)
            }.copy(notificationVariant = dailyNotificationType)
        }

        private fun createDailyTotalTrackersNotification(
            totalTrackersCount: Int,
            apps: Int,
            firstAppName: String,
        ): DeviceShieldNotification {
            val textToStyle = if (totalTrackersCount == 1) {
                if (apps == 0) {
                    resources.getString(R.string.atp_DailyTrackersNotificationOneTrackingZeroApps, totalTrackersCount)
                } else {
                    resources.getString(R.string.atp_DailyTrackersNotificationOneTrackingOneApps, totalTrackersCount, firstAppName)
                }
            } else {
                if (apps == 0) {
                    resources.getString(R.string.atp_DailyTrackersNotificationOtherTrackingZeroApps, totalTrackersCount)
                } else {
                    resources.getString(R.string.atp_DailyTrackersNotificationOtherTrackingOtherApps, totalTrackersCount, apps, firstAppName)
                }
            }

            logcat { "createDailyTotalTrackersNotification. Trackers=$totalTrackersCount. Apps=$apps. Output=[$textToStyle]" }
            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }

        private fun createDailyTopTrackerCompanyNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val topOffender = getTopOffender(trackers)
            val numberApps = getNumberOfAppsContainingTopOffender(trackers, topOffender).size

            val textToStyle = resources.getQuantityString(
                R.plurals.atp_DailyTopCompanyNotification,
                numberApps,
                topOffender.companyDisplayName,
                numberApps,
            )

            logcat { "createDailyTopTrackerCompanyNotification: $textToStyle" }
            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }

        private fun createDailyNotificationTopAppsContainingTrackers(apps: List<Pair<TrackingApp, List<VpnTracker>>>): DeviceShieldNotification {
            if (apps.isEmpty()) {
                return DeviceShieldNotification(hidden = true)
            }

            val firstAppName = apps.first().first.appDisplayName
            val secondAppName = apps.getOrNull(1)?.first?.appDisplayName ?: ""

            val textToStyle = resources.getQuantityString(R.plurals.atp_DailyCompanyBlockedNotification, apps.size, firstAppName, secondAppName)

            logcat { "createDailyNotificationTopAppsContainingTrackers. Text to style: [$textToStyle]" }
            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }

        private fun createDailyLastCompanyAttemptNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val lastCompany = trackers.first()
            val latestApp = lastCompany.trackingApp.appDisplayName
            val filteredForLatestTrackerCompany = trackers.filter { it.trackerCompanyId == lastCompany.trackerCompanyId }
            val timesBlocked = filteredForLatestTrackerCompany.size
            val appsContainingLatestTracker = filteredForLatestTrackerCompany.groupBy { it.trackingApp }
            val otherApps = (appsContainingLatestTracker.size - 1).coerceAtLeast(0)

            val textToStyle = if (timesBlocked == 1) {
                when (otherApps) {
                    0 -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOneTimeZeroOtherApps,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                    )
                    1 -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOneTimeOneOtherApp,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                    )
                    else -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOneTimeMoreOtherApps,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                        otherApps,
                    )
                }
            } else {
                when (otherApps) {
                    0 -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOtherTimesZeroOtherApps,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                    )
                    1 -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOtherTimesOneOtherApp,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                    )
                    else -> resources.getString(
                        R.string.atp_DailyLastCompanyBlockedNotificationOtherTimesMoreOtherApps,
                        lastCompany.companyDisplayName,
                        timesBlocked,
                        latestApp,
                        otherApps,
                    )
                }
            }

            logcat { "createDailyLastCompanyAttemptNotification. [$textToStyle]" }
            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }

        private fun getTopOffender(trackers: List<VpnTracker>): VpnTracker {
            val perCompany = trackers.groupBy { it.trackerCompanyId }
            var topOffender = perCompany.values.first()
            perCompany.values.forEach {
                if (it.size > topOffender.size) {
                    topOffender = it
                }
            }

            return topOffender.first()
        }
    }

    inner class DeviceShieldWeeklyNotificationFactory {

        suspend fun createWeeklyDeviceShieldNotification(randomNumber: Int): DeviceShieldNotification {
            val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastWeek() })
            val trackerCount =
                appTrackerBlockingStatsRepository.getBlockedTrackersCountBetween({ dateOfLastDay() }).firstOrNull() ?: trackers.sumOf { it.count }
            if (trackers.isEmpty()) {
                return DeviceShieldNotification(hidden = true)
            }

            return when (randomNumber) {
                0 -> createWeeklyReportNotification(trackerCount, trackers)
                else -> createWeeklyTopTrackerCompanyNotification(trackers)
            }
        }

        private fun createWeeklyReportNotification(
            trackerCount: Int,
            trackers: List<VpnTracker>,
        ): DeviceShieldNotification {
            val perApp = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.size }
            val otherAppsSize = (perApp.size - 1).coerceAtLeast(0)
            val latestApp = perApp.first().first.appDisplayName

            val textToStyle = if (trackerCount == 1) {
                when (otherAppsSize) {
                    0 -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOneTimeZeroOtherApps,
                        trackerCount,
                        latestApp,
                    )
                    1 -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOneTimeOneOtherApp,
                        trackerCount,
                        latestApp,
                    )
                    else -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOneTimeMoreOtherApps,
                        trackerCount,
                        latestApp,
                        otherAppsSize,
                    )
                }
            } else {
                when (otherAppsSize) {
                    0 -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOtherTimesZeroOtherApps,
                        trackerCount,
                        latestApp,
                    )
                    1 -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOtherTimesOneOtherApp,
                        trackerCount,
                        latestApp,
                    )
                    else -> resources.getString(
                        R.string.atp_WeeklyCompanyTrackersBlockedNotificationOtherTimesMoreOtherApps,
                        trackerCount,
                        latestApp,
                        otherAppsSize,
                    )
                }
            }

            logcat { "createWeeklyReportNotification. $textToStyle\nTotal apps: ${perApp.size}. Other apps: $otherAppsSize" }

            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }

        private fun createWeeklyTopTrackerCompanyNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val perCompany: Map<Int, List<VpnTracker>> = trackers.groupBy { it.trackerCompanyId }
            var topOffender = perCompany.values.first()
            perCompany.values.forEach {
                if (it.size > topOffender.size) {
                    topOffender = it
                }
            }
            val company = topOffender.first().companyDisplayName
            val appsContainingTrackerEntity = getNumberOfAppsContainingTopOffender(trackers, topOffender.first())
            val numberOfApps = appsContainingTrackerEntity.size
            val mostRecentAppContainingTracker = trackers.firstOrNull { it.trackerCompanyId == topOffender.first().trackerCompanyId }
                ?: return DeviceShieldNotification(hidden = true)

            val textToStyle = resources.getQuantityString(
                R.plurals.atp_WeeklyCompanyTeaserNotification,
                numberOfApps,
                company,
                numberOfApps,
                mostRecentAppContainingTracker.trackingApp.appDisplayName,
            )

            logcat { "createWeeklyTopTrackerCompanyNotification. text=$textToStyle" }
            return DeviceShieldNotification(
                SpannableStringBuilder(HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)),
            )
        }
    }

    data class DeviceShieldNotification(
        val text: SpannableStringBuilder = SpannableStringBuilder(),
        val silent: Boolean = false,
        val hidden: Boolean = false,
        val notificationVariant: Int = -1, // default no variant
    ) {
        companion object {
            fun from(content: VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent): DeviceShieldNotification {
                return DeviceShieldNotification(
                    text = SpannableStringBuilder(content.text),
                )
            }
        }
    }
}
