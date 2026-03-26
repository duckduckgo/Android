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

import android.content.res.Resources
import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import com.duckduckgo.common.utils.formatters.time.model.dateOfLastHour
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.NotificationActions
import com.duckduckgo.mobile.android.vpn.service.VpnEnabledNotificationContentPlugin.VpnEnabledNotificationContent
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class AppTPAndNetPEnabledNotificationContentPlugin @Inject constructor(
    private val resources: Resources,
    private val repository: AppTrackerBlockingStatsRepository,
    private val appTrackingProtection: AppTrackingProtection,
    private val networkProtectionState: NetworkProtectionState,
    appTpEnabledNotificationIntentProvider: AppTpEnabledNotificationContentPlugin.IntentProvider,
) : VpnEnabledNotificationContentPlugin {

    private val notificationPendingIntent by lazy { appTpEnabledNotificationIntentProvider.getOnPressNotificationIntent() }
    private val deletePendingIntent by lazy { appTpEnabledNotificationIntentProvider.getDeleteNotificationIntent() }

    override val uuid: String = "1cc717cf-f046-40de-948c-fd8bc26300d4"

    override fun getInitialContent(): VpnEnabledNotificationContent? {
        return if (isActive()) {
            val text = networkProtectionState.serverLocation()?.run {
                HtmlCompat.fromHtml(
                    resources.getString(R.string.vpn_SilentNotificationTitleAppTPAndNetpEnabledNoneBlocked, this),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                )
            } ?: resources.getString(R.string.vpn_SilentNotificationTitleAppTPAndNetpEnabledNoneBlockedNoLocation)
            return VpnEnabledNotificationContent(
                title = null,
                text = SpannableStringBuilder(text),
                onNotificationPressIntent = notificationPendingIntent,
                notificationActions = NotificationActions.VPNActions,
                deleteIntent = deletePendingIntent,
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
            .filter { isActive() } // make sure we only emit when this plugin is active
            .map { trackersBlocked ->
                val trackingApps = trackersBlocked.trackingApps()
                val location = networkProtectionState.serverLocation()
                val notificationText = if (trackersBlocked.isEmpty() || trackingApps.isEmpty()) {
                    location?.run {
                        HtmlCompat.fromHtml(
                            resources.getString(
                                R.string.vpn_SilentNotificationTitleAppTPAndNetpEnabledNoneBlocked,
                                this,
                            ),
                            HtmlCompat.FROM_HTML_MODE_LEGACY,
                        )
                    } ?: resources.getString(R.string.vpn_SilentNotificationTitleAppTPAndNetpEnabledNoneBlockedNoLocation)
                } else {
                    location?.run {
                        HtmlCompat.fromHtml(
                            resources.getQuantityString(
                                R.plurals.vpn_SilentNotificationTitleAppTPAndNetpEnabledBlocked,
                                trackingApps.size,
                                this,
                                trackingApps.size,
                            ),
                            HtmlCompat.FROM_HTML_MODE_LEGACY,
                        )
                    } ?: HtmlCompat.fromHtml(
                        resources.getQuantityString(
                            R.plurals.vpn_SilentNotificationTitleAppTPAndNetpEnabledBlockedNoLocation,
                            trackingApps.size,
                            trackingApps.size,
                        ),
                        HtmlCompat.FROM_HTML_MODE_LEGACY,
                    )
                }

                VpnEnabledNotificationContent(
                    title = null,
                    text = SpannableStringBuilder(notificationText),
                    onNotificationPressIntent = notificationPendingIntent,
                    notificationActions = NotificationActions.VPNActions,
                    deleteIntent = deletePendingIntent,
                )
            }
    }

    override fun getPriority(): VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority {
        return VpnEnabledNotificationContentPlugin.VpnEnabledNotificationPriority.HIGH
    }

    override fun isActive(): Boolean {
        return runBlocking { appTrackingProtection.isEnabled() && networkProtectionState.isEnabled() }
    }
}
