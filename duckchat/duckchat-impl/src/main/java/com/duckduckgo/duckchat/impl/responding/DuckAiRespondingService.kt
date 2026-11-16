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

package com.duckduckgo.duckchat.impl.responding

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.android.AndroidInjection
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.FOREGROUND_FAILED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.FOREGROUND_NOT_ALLOWED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@InjectWith(scope = ServiceScope::class)
class DuckAiRespondingService : Service() {

    @Inject
    internal lateinit var supervisor: RespondingServiceSupervisor

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private var latestStartId = 0

    private var keepServiceRunningCollector: Job? = null

    private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        latestStartId = startId
        val notification = try {
            createNotificationChannel()
            buildNotification()
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai responding service notification setup failed: ${e::class.simpleName}" }
            buildFallbackNotification()
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (appBuildConfig.sdkInt >= 34) FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0,
            )
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai responding service startForeground failed: ${e::class.simpleName}" }
            supervisor.onStartFailed(if (e is IllegalStateException) FOREGROUND_NOT_ALLOWED else FOREGROUND_FAILED)
            if (keepServiceRunningCollector == null) stopSelfSafely(startId)
            return START_NOT_STICKY
        }
        if (keepServiceRunningCollector == null) {
            // Initialize the collector only after the first start command so there's a valid startId to use for stopSelf.
            keepServiceRunningCollector = CoroutineScope(dispatcherProvider.main()).launch {
                supervisor.keepServiceRunning.collect { keepRunning ->
                    // Using the latest startId ensures the service will be stopped.
                    // At the same time, if there's a start command pending, stopSelf(startId) is a no-op, and won't crash.
                    if (!keepRunning) stopSelfSafely(latestStartId)
                }
            }
        } else if (!supervisor.keepServiceRunning.value) {
            // The keepServiceRunning changed to false while this start command was pending, so we need to handle it directly.
            stopSelfSafely(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        onTimedOut()
    }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        onTimedOut()
    }

    override fun onDestroy() {
        keepServiceRunningCollector?.cancel()
        super.onDestroy()
    }

    private fun onTimedOut() {
        if (timedOut) return
        timedOut = true
        supervisor.onServiceTimedOut()
        stopSelfSafely()
    }

    private fun stopSelfSafely(startId: Int? = null) {
        try {
            if (startId == null) stopSelf() else stopSelf(startId)
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai responding service stopSelf failed: ${e::class.simpleName}" }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.duckAiRespondingNotificationTitle))
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(launchPendingIntent())
            .build()

    private fun buildFallbackNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .build()

    private fun launchPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: return null
        return PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.duckAiRespondingNotificationChannelName),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_ID = 9200
        const val CHANNEL_ID = "duck_ai_responding"
    }
}
