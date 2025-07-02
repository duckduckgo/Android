/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.scan

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.common.PirJob.RunType.MANUAL
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity.Companion.NOTIF_CHANNEL_ID
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity.Companion.NOTIF_ID_STATUS_COMPLETE
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import logcat.logcat

@InjectWith(scope = ServiceScope::class)
class PirForegroundScanService : Service(), CoroutineScope by MainScope() {
    @Inject
    lateinit var pirScan: PirScan

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        logcat { "PIR-SCAN: PIR service started on ${Process.myPid()} thread: ${Thread.currentThread().name}" }
        val notification: Notification =
            createNotification(getString(R.string.pirNotificationMessageInProgress))
        startForeground(1, notification)

        launch {
            val result = pirScan.executeAllBrokers(this@PirForegroundScanService, MANUAL)
            if (result.isSuccess) {
                notificationManagerCompat.checkPermissionAndNotify(
                    applicationContext,
                    NOTIF_ID_STATUS_COMPLETE,
                    createNotification(getString(R.string.pirNotificationMessageComplete)),
                )
            }
            stopSelf()
        }

        logcat { "PIR-SCAN: START_NOT_STICKY" }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logcat { "PIR-SCAN: PIR service destroyed" }
        pirScan.stop()
    }

    private fun createNotification(message: String): Notification {
        val notificationIntent = Intent(
            this,
            PirDevSettingsActivity::class.java,
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.pirNotificationTitle))
            .setContentText(message)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentIntent(pendingIntent)
            .build()
    }
}
