/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.health

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.health.SystemHealthData
import com.duckduckgo.vpn.internal.R
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface HealthNotificationManager {
    fun showBadHealthNotification(
        reasonsForAlert: List<String>,
        systemHealth: SystemHealthData
    )

    fun hideBadHealthNotification()
}

@ContributesBinding(AppScope::class)
class RealHealthNotificationManager @Inject constructor(private val context: Context) : HealthNotificationManager {

    override fun showBadHealthNotification(
        reasonsForAlert: List<String>,
        systemHealth: SystemHealthData
    ) {

        val target = Intent().also {
            it.setClassName(context.packageName, "dummy.ui.VpnDiagnosticsActivity")
        }

        val pendingIntent = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(target)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)

        // todo - make these do something different
        val assertGoodPendingIntent = pendingIntent
        val assertBadPendingIntent = assertGoodPendingIntent

        val notification = NotificationCompat.Builder(context, "notificationid")
            .setSmallIcon(R.drawable.ic_baseline_mood_bad_24)
            .setContentTitle("AppTP Health Monitor")
            .setContentText(String.format("It looks like AppTP might be in bad health."))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonsForAlert.joinToString(separator = "\n")))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH)
            .addAction(0, "All good", assertGoodPendingIntent)
            .addAction(0, "There's a problem", assertBadPendingIntent)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(TimeUnit.MINUTES.toMillis(10))
            .build()

        NotificationManagerCompat.from(context).let { nm ->

            val channelBuilder =
                NotificationChannelCompat.Builder("notificationid", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("notificationid")
            nm.createNotificationChannel(channelBuilder.build())

            nm.notify(BAD_HEALTH_NOTIFICATION_ID, notification)
        }
    }

    override fun hideBadHealthNotification() {
        NotificationManagerCompat.from(context).cancel(BAD_HEALTH_NOTIFICATION_ID)
    }

    companion object {
        const val BAD_HEALTH_NOTIFICATION_ID = 9890
    }
}
