/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.responseready

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import logcat.logcat

// POC: keeps the process and its network alive while the monitor polls Sync in the background.
class DuckAiRespondingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Duck.ai is responding", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentTitle("Duck.ai is responding…")
            .setSilent(true)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            logcat(tag = TAG) { "POC: FGS started" }
        } catch (e: Exception) {
            logcat(tag = TAG) { "POC: FGS startForeground failed: $e" }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        logcat(tag = TAG) { "POC: FGS onTimeout" }
        stopSelf()
    }

    override fun onDestroy() {
        logcat(tag = TAG) { "POC: FGS destroyed" }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DuckAiResponseMonitor"
        private const val CHANNEL_ID = "duck_ai_responding"
        private const val NOTIFICATION_ID = 9200

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DuckAiRespondingService::class.java))
            } catch (e: Exception) {
                logcat(tag = TAG) { "POC: FGS start not allowed: $e" }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DuckAiRespondingService::class.java))
        }
    }
}
