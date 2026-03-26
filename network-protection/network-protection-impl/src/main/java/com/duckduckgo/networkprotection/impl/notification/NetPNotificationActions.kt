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

package com.duckduckgo.networkprotection.impl.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider

interface NetPNotificationActions {
    fun getReportIssueNotificationAction(context: Context): NotificationCompat.Action
    fun getEnableNetpNotificationAction(context: Context): NotificationCompat.Action
}

@ContributesBinding(AppScope::class)
class RealNetPNotificationActions @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    @NetpBreakageCategories private val breakageCategories: Provider<List<AppBreakageCategory>>,
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback,
) : NetPNotificationActions {
    override fun getReportIssueNotificationAction(context: Context): NotificationCompat.Action {
        val launchIntent = if (runBlocking { privacyProUnifiedFeedback.shouldUseUnifiedFeedback(VPN_MANAGEMENT) }) {
            globalActivityStarter.startIntent(
                context,
                PrivacyProFeedbackScreenWithParams(feedbackSource = VPN_MANAGEMENT),
            )
        } else {
            globalActivityStarter.startIntent(
                context,
                OpenVpnBreakageCategoryWithBrokenApp(
                    launchFrom = "netp",
                    appName = "",
                    appPackageId = "",
                    breakageCategories = breakageCategories.get(),
                ),
            )
        }
        return NotificationCompat.Action(
            R.drawable.ic_baseline_feedback_24,
            context.getString(R.string.netpNotificationCTAReportIssue),
            PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE),
        )
    }

    override fun getEnableNetpNotificationAction(context: Context): NotificationCompat.Action {
        val launchIntent = Intent(context, NetPEnableReceiver::class.java).let { intent ->
            intent.action = NetPEnableReceiver.ACTION_NETP_ENABLE
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Action(
            R.drawable.ic_vpn_notification_24,
            context.getString(R.string.netpNotificationCTAEnableNetp),
            launchIntent,
        )
    }
}
