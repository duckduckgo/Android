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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageAppListActivity

class NotificationActionReportIssue {

    companion object {
        fun reportIssueNotificationAction(context: Context): NotificationCompat.Action {
            val launchIntent = ReportBreakageAppListActivity.intent(context).also {
                it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return NotificationCompat.Action(
                R.drawable.ic_baseline_feedback_24,
                context.getString(R.string.atp_ReportIssue),
                PendingIntent.getActivity(context, 0, launchIntent, pendingIntentFlags())
            )
        }

        private fun pendingIntentFlags(): Int {
            return PendingIntent.FLAG_IMMUTABLE
        }
    }
}
