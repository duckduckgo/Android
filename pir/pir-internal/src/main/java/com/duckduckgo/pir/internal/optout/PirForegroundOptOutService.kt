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

package com.duckduckgo.pir.internal.optout

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
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity.Companion.NOTIF_CHANNEL_ID
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity.Companion.NOTIF_ID_STATUS_COMPLETE
import dagger.android.AndroidInjection
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat

@InjectWith(scope = ServiceScope::class)
class PirForegroundOptOutService : Service(), CoroutineScope by MainScope() {
    @Inject
    lateinit var pirOptOut: PirOptOut

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    private val serviceDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        // TODO find correct place.
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
        logcat { "PIR-OPT-OUT: PIR service started on ${Process.myPid()} thread: ${Thread.currentThread().name}" }
        val notification: Notification =
            createNotification(getString(R.string.pirOptOutNotificationMessageInProgress))
        startForeground(1, notification)

        synchronized(this) {
            launch(serviceDispatcher) {
                async {
                    val brokers = intent?.getStringExtra(EXTRA_BROKER_TO_OPT_OUT)

                    val result = if (!brokers.isNullOrEmpty()) {
                        pirOptOut.execute(listOf(brokers), this@PirForegroundOptOutService, this)
                    } else {
                        pirOptOut.executeForBrokersWithRecords(this@PirForegroundOptOutService, this)
                    }

                    if (result.isSuccess) {
                        notificationManagerCompat.checkPermissionAndNotify(
                            applicationContext,
                            NOTIF_ID_STATUS_COMPLETE,
                            createNotification(getString(R.string.pirOptOutNotificationMessageComplete)),
                        )
                    }
                    stopSelf()
                }.await()
            }
        }

        logcat { "PIR-OPT-OUT: START_NOT_STICKY" }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logcat { "PIR-OPT-OUT: PIR service destroyed" }
        pirOptOut.stop()
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
            .setContentTitle(getString(R.string.pirOptOutNotificationTitle))
            .setContentText(message)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        internal const val EXTRA_BROKER_TO_OPT_OUT = "EXTRA_BROKER_TO_OPT_OUT"
    }
}
