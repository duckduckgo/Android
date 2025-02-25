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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.internal.settings.PirDevSettings
import com.duckduckgo.pir.internal.settings.PirDevSettings.Companion.NOTIF_CHANNEL_ID
import dagger.android.AndroidInjection
import javax.inject.Inject
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat

@InjectWith(scope = ServiceScope::class)
class PirForegroundScanService : Service() {
    @Inject
    lateinit var pirScan: PirScan

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        LogcatLogger.install(AndroidLogcatLogger(LogPriority.DEBUG))
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        logcat { "PIR-SCAN: PirForegroundScanService onStartCommand" }
        logcat { "PIR-SCAN: Process ${Process.myPid()} thread: ${Thread.currentThread().name}" }
        val notification: Notification = createNotification()
        startForeground(1, notification)

        // pirScan.execute(listOf("Clubset", "PeopleFinders", "backgroundcheck.run", "FastBackgroundCheck.com", "Verecor"), this) {
        pirScan.executeAllBrokers(this) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logcat { "PIR-SCAN: PirForegroundScanService onDestroy" }
        pirScan.stop()
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(
            this,
            PirDevSettings::class.java,
        )
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Pir Manual Scan")
            .setContentText("Manual scan is currently in progress")
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentIntent(pendingIntent)
            .build()
    }
}
