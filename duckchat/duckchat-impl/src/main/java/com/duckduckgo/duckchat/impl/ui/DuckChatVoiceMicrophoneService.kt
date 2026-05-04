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

package com.duckduckgo.duckchat.impl.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.duckchat.impl.R
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive and signals to Android that microphone access
 * is intentionally used while Duck.ai voice mode is active in the background.
 */
@InjectWith(scope = ServiceScope::class)
class DuckChatVoiceMicrophoneService : Service() {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (appBuildConfig.sdkInt >= 30) {
                FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.duckAiVoiceNotificationTitle))
            .setContentText(getString(R.string.duckAiVoiceNotificationMessage))
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.duckAiVoiceNotificationChannelName),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 9100
        private const val CHANNEL_ID = "duck_ai_voice_microphone"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DuckChatVoiceMicrophoneService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DuckChatVoiceMicrophoneService::class.java))
        }
    }
}
