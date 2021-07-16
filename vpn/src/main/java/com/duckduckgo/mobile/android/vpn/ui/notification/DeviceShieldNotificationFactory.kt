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
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.dateOfLastDay
import com.duckduckgo.mobile.android.vpn.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import timber.log.Timber
import javax.inject.Inject

class DeviceShieldNotificationFactory @Inject constructor(
    private val resources: Resources,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository
) {

    @VisibleForTesting
    val dailyNotificationFactory = DeviceShieldDailyNotificationFactory()

    @VisibleForTesting
    val weeklyNotificationFactory = DeviceShieldWeeklyNotificationFactory()

    fun createDailyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..3).shuffled().first()
        return dailyNotificationFactory.createDailyDeviceShieldNotification(randomNumber)
    }

    fun createWeeklyDeviceShieldNotification(): DeviceShieldNotification {
        val randomNumber = (0..1).shuffled().first()
        return weeklyNotificationFactory.createWeeklyDeviceShieldNotification(randomNumber)
    }

    fun createNotificationDeviceShieldEnabled(): DeviceShieldNotification {
        val title = SpannableStringBuilder(resources.getString(R.string.atp_OnInitialNotification))
        return DeviceShieldNotification(title)
    }

    fun createNotificationNewTrackerFound(trackersBlocked: List<VpnTracker>): DeviceShieldNotification {
        val numberOfApps = trackersBlocked.distinctBy { it.trackingApp.packageId }
        if (trackersBlocked.isEmpty() || numberOfApps.isEmpty()) return DeviceShieldNotification(SpannableStringBuilder(resources.getString(R.string.atp_OnNoTrackersNotificationHeader)))

        val prefix = resources.getString(R.string.atp_OnNotificationPrefix)
        val numberOfAppsString = resources.getQuantityString(R.plurals.atp_NotificationNumberOfApps, numberOfApps.size, numberOfApps.size)
        val suffixTime = resources.getString(R.string.atp_OnNoTrackersNotificationMessageTimeSuffix)
        val notificationText = "$prefix$numberOfAppsString $suffixTime"

        Timber.i("createTrackersCountDeviceShieldNotification [$notificationText]")
        return DeviceShieldNotification(
            notificationText.applyBoldSpanTo(
                listOf(
                    numberOfAppsString
                )
            )
        )
    }

    private fun getNumberOfAppsContainingTopOffender(trackers: List<VpnTracker>, topOffender: VpnTracker): Map<TrackingApp, List<VpnTracker>> {
        return trackers.filter { it.trackerCompanyId == topOffender.trackerCompanyId }.groupBy { it.trackingApp }
    }

    inner class DeviceShieldDailyNotificationFactory {

        fun createDailyDeviceShieldNotification(dailyNotificationType: Int): DeviceShieldNotification {

            val trackers = appTrackerBlockingStatsRepository.getVpnTrackersSync({ dateOfLastDay() })
            Timber.i("createDailyDeviceShieldNotification. ${trackers.size} trackers in the last day. Notification type: $dailyNotificationType")

            if (trackers.isEmpty()) {
                return DeviceShieldNotification(hidden = true)
            }

            val apps = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.size }
            val firstAppName = apps.firstOrNull()?.first?.appDisplayName ?: ""

            return when (dailyNotificationType) {
                0 -> createDailyTotalTrackersNotification(trackers, apps.size, firstAppName)
                1 -> createDailyTopTrackerCompanyNotification(trackers)
                2 -> createDailyNotificationTopAppsContainingTrackers(apps)
                else -> createDailyLastCompanyAttemptNotification(trackers)
            }.copy(notificationVariant = dailyNotificationType)
        }

        private fun createDailyTotalTrackersNotification(trackers: List<VpnTracker>, apps: Int, firstAppName: String): DeviceShieldNotification {
            val totalTrackers = resources.getQuantityString(R.plurals.atp_TrackingAttempts, trackers.size, trackers.size)
            val textPrefix = resources.getString(R.string.atp_DailyTrackersNotificationPrefix)
            val numberTrackers = resources.getQuantityString(R.plurals.atp_TrackingAttempts, trackers.size, trackers.size)
            val optionalNumberApps = if (apps == 0) "" else {
                " ${resources.getQuantityString(R.plurals.atp_DailyTrackersNotificationSuffixNumApps, apps, apps, firstAppName)}"
            }
            val textSuffix = resources.getString(R.string.atp_DailyNotificationPastDaySuffix)
            val textToStyle = "$textPrefix $numberTrackers$optionalNumberApps $textSuffix"

            Timber.i("createDailyTotalTrackersNotification. Trackers=${trackers.size}. Apps=$apps. Output=[$textToStyle]")
            return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(totalTrackers)))
        }

        private fun createDailyTopTrackerCompanyNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val topOffender = getTopOffender(trackers)
            val numberApps = getNumberOfAppsContainingTopOffender(trackers, topOffender).size

            val prefix = resources.getString(R.string.atp_DailyTopCompanyNotificationPrefix, topOffender.companyDisplayName)
            val numAppsText = resources.getQuantityString(R.plurals.atp_NotificationNumberOfApps, numberApps, numberApps)
            val pastDaySuffix = resources.getString(R.string.atp_NotificationPastDaySuffix)
            val seeMoreSuffix = resources.getString(R.string.atp_NotificationSeeMoreSuffix)
            val fullString = "$prefix$numAppsText $pastDaySuffix $seeMoreSuffix"

            Timber.i("createDailyTopTrackerCompanyNotification: $fullString")
            return DeviceShieldNotification(fullString.applyBoldSpanTo(listOf(topOffender.companyDisplayName, seeMoreSuffix)))
        }

        private fun createDailyNotificationTopAppsContainingTrackers(apps: List<Pair<TrackingApp, List<VpnTracker>>>): DeviceShieldNotification {
            if (apps.isEmpty()) {
                return DeviceShieldNotification(hidden = true)
            }

            val firstAppName = apps.first().first.appDisplayName
            val second: TrackingApp? = apps.getOrNull(1)?.first

            val prefix = resources.getString(R.string.atp_DailyCompanyBlockedNotificationPrefix, firstAppName)
            val optionalSecondApp =
                if (second != null) " ${resources.getString(R.string.atp_DailyCompanyBlockedNotificationOptionalSecondApp, second.appDisplayName)}" else ""

            val suffix = resources.getString(R.string.atp_DailyNotificationPastDaySuffix)

            val textToStyle = "$prefix$optionalSecondApp $suffix"
            val wordsToBold = mutableListOf(firstAppName)
            if (second != null) wordsToBold.add(second.appDisplayName)

            Timber.i("createDailyNotificationTopAppsContainingTrackers. Text to style: [$textToStyle] Words to bold: ${wordsToBold.joinToString(separator = ", ")}}")
            return DeviceShieldNotification(textToStyle.applyBoldSpanTo(wordsToBold))
        }

        private fun createDailyLastCompanyAttemptNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val lastCompany = trackers.first()
            val latestApp = lastCompany.trackingApp.appDisplayName
            val filteredForLatestTrackerCompany = trackers.filter { it.trackerCompanyId == lastCompany.trackerCompanyId }
            val timesBlocked = filteredForLatestTrackerCompany.size
            val appsContainingLatestTracker = filteredForLatestTrackerCompany.groupBy { it.trackingApp }

            val prefix = resources.getString(R.string.atp_DailyLastCompanyBlockedNotification, lastCompany.companyDisplayName)
            val numberOfTimesString = resources.getQuantityString(R.plurals.atp_NumberTimes, timesBlocked, timesBlocked)
            val latestAppString = resources.getString(R.string.atp_DailyLastCompanyBlockedNotificationInApp, latestApp)
            val otherApps = (appsContainingLatestTracker.size - 1).coerceAtLeast(0)

            val otherAppsCount = if (otherApps == 0) "" else resources.getQuantityString(
                R.plurals.atp_DailyLastCompanyBlockedNotificationOptionalOtherApps,
                otherApps,
                otherApps
            )
            val pastDaySuffix = resources.getString(R.string.atp_DailyNotificationPastDaySuffix)

            val textToStyle = "$prefix $numberOfTimesString $latestAppString$otherAppsCount $pastDaySuffix"
            Timber.i("createDailyLastCompanyAttemptNotification. [$textToStyle]")
            return DeviceShieldNotification(textToStyle.applyBoldSpanTo(listOf(lastCompany.companyDisplayName)))
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

        private fun createWeeklyReportNotification(trackers: List<VpnTracker>): DeviceShieldNotification {
            val perApp = trackers.groupBy { it.trackingApp }.toList().sortedByDescending { it.second.size }
            val otherAppsSize = (perApp.size - 1).coerceAtLeast(0)
            val latestApp = perApp.first().first.appDisplayName
            val latestAppString = resources.getString(R.string.atp_DailyLastCompanyBlockedNotificationInApp, latestApp)

            val prefix = resources.getString(R.string.atp_WeeklyCompanyTrackersBlockedNotificationPrefix)
            val totalTrackers = resources.getQuantityString(R.plurals.atp_TrackingAttempts, trackers.size, trackers.size)
            val optionalOtherAppsString = if (otherAppsSize == 0) "" else resources.getQuantityString(
                R.plurals.atp_DailyLastCompanyBlockedNotificationOptionalOtherApps, otherAppsSize, otherAppsSize
            )
            val pastWeekSuffix = resources.getString(R.string.atp_WeeklyCompanyTeaserNotificationSuffix)

            val textToStyle = "$prefix $totalTrackers $latestAppString$optionalOtherAppsString$pastWeekSuffix"

            Timber.i("createWeeklyReportNotification. $textToStyle\nTotal apps: ${perApp.size}. Other apps: $otherAppsSize")

            return DeviceShieldNotification(
                textToStyle.applyBoldSpanTo(
                    listOf(
                        totalTrackers,
                        latestAppString
                    )
                )
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
            val prefixString = resources.getString(R.string.atp_WeeklyCompanyTeaserNotificationPrefix, company)
            val numberOfApps = appsContainingTrackerEntity.size
            val mostRecentAppContainingTracker = trackers.firstOrNull { it.trackerCompanyId == topOffender.first().trackerCompanyId }
                ?: return DeviceShieldNotification(hidden = true)

            val numberOfAppsString = resources.getQuantityString(R.plurals.atp_NotificationNumberOfApps, numberOfApps, numberOfApps)
            val mostRecentAppString =
                resources.getQuantityString(
                    R.plurals.atp_WeeklyCompanyTeaserNotificationIncludingApp,
                    numberOfApps,
                    mostRecentAppContainingTracker.trackingApp.appDisplayName
                )
            val suffixString = resources.getString(R.string.atp_WeeklyCompanyTeaserNotificationSuffix)

            val textToStyle = "$prefixString$numberOfAppsString$mostRecentAppString$suffixString"
            Timber.i("createWeeklyTopTrackerCompanyNotification. text=$textToStyle")
            return DeviceShieldNotification(
                textToStyle.applyBoldSpanTo(
                    listOf(
                        company,
                        numberOfAppsString,
                        mostRecentAppString
                    )
                )
            )
        }

    }

    data class DeviceShieldNotification(
        val title: SpannableStringBuilder = SpannableStringBuilder(),
        val message: SpannableStringBuilder = SpannableStringBuilder(),
        val silent: Boolean = false,
        val hidden: Boolean = false,
        val notificationVariant: Int = -1, // default no variant
    )
}
