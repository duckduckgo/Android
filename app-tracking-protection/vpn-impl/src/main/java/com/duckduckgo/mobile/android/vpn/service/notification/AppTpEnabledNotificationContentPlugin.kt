/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service.notification

import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import android.text.SpannableStringBuilder
import androidx.core.app.TaskStackBuilder
import androidx.core.text.HtmlCompat
import com.duckduckgo.app.global.formatters.time.model.dateOfLastHour
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.notification.OngoingNotificationPressedHandler
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class AppTpEnabledNotificationContentPlugin @Inject constructor(
    private val context: Context,
    private val resources: Resources,
    private val repository: AppTrackerBlockingStatsRepository,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val notificationPressHandler: OngoingNotificationPressedHandler,
) : VpnEnabledNotificationContentPlugin {

    override fun getInitialContent(): VpnEnabledNotificationContent? {
        return if (vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)) {
            return VpnEnabledNotificationContent(
                title = SpannableStringBuilder(resources.getString(R.string.atp_OnInitialNotification)),
                message = SpannableStringBuilder(),
            )
        } else {
            null
        }
    }

    override fun getUpdatedContent(): Flow<VpnEnabledNotificationContent> {
        fun List<VpnTracker>.trackingApps(): List<VpnTracker> {
            return this.distinctBy { it.trackingApp.packageId }
        }

        return repository.getVpnTrackers({ dateOfLastHour() })
            .map { trackersBlocked ->
                val trackingApps = trackersBlocked.trackingApps()
                val notificationText = if (!vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)) {
                    ""
                } else if (trackersBlocked.isEmpty() || trackingApps.isEmpty()) {
                    resources.getString(R.string.atp_OnNoTrackersNotificationHeader)
                } else {
                    resources.getQuantityString(R.plurals.atp_OnNotification, trackingApps.size, trackingApps.size)
                }
                VpnEnabledNotificationContent(
                    title = SpannableStringBuilder(HtmlCompat.fromHtml(notificationText, HtmlCompat.FROM_HTML_MODE_LEGACY)),
                    message = SpannableStringBuilder(),
                )
            }
    }

    override fun getOnPressNotificationIntent(): PendingIntent? {
        return if (vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)) {
            val privacyReportIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = notificationPressHandler)
            TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(privacyReportIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        } else {
            null
        }
    }

    override fun getPriority(): VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority {
        return VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.NORMAL
    }
}
