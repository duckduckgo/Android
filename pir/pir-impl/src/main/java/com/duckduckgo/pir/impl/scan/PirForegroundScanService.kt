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

package com.duckduckgo.pir.impl.scan

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.ServiceCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.duckduckgo.pir.impl.scheduling.PirJobsRunner
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@InjectWith(scope = ServiceScope::class)
class PirForegroundScanService : Service(), CoroutineScope by MainScope() {
    @Inject
    lateinit var pirJobsRunner: PirJobsRunner

    @Inject
    lateinit var pirNotificationManager: PirNotificationManager

    @Inject
    lateinit var pirWorkHandler: PirWorkHandler

    @Inject
    lateinit var pirFeatureDataCleaner: PirFeatureDataCleaner

    @Inject
    lateinit var pirPixelSender: PirPixelSender

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
        val notification: Notification = pirNotificationManager.createScanStatusNotification(
            title = getString(R.string.pirFeatureName),
            message = getString(R.string.pirNotificationMessageInProgress),
        )
        try {
            ServiceCompat.startForeground(
                this,
                PIR_SCAN_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (_: Exception) {
            logcat(LogPriority.ERROR) { "PIR-SCAN: Could not start the service as foreground!" }
            pirPixelSender.reportManualScanStartFailed()
            // If we can't start as a foreground service, there's no point in continuing.
            stopSelf()
            return START_NOT_STICKY
        }

        launch {
            if (pirWorkHandler.canRunPir().firstOrNull() == false) {
                logcat { "PIR-SCAN: PIR scan not allowed to run!" }
                pirWorkHandler.cancelWork()
                pirFeatureDataCleaner.removeAllData()
                stopSelf()
                return@launch
            }

            val result = pirJobsRunner.runEligibleJobs(this@PirForegroundScanService, PirExecutionType.MANUAL)
            if (result.isSuccess) {
                pirNotificationManager.showScanStatusNotification(
                    title = getString(R.string.pirNotificationTitleComplete),
                    message = getString(R.string.pirNotificationMessageComplete),
                )
            }
            stopSelf()
        }

        logcat { "PIR-SCAN: START_NOT_STICKY" }
        return START_NOT_STICKY
    }

    override fun onLowMemory() {
        logcat(WARN) { "PIR-SCAN: onLowMemory called" }
        pirPixelSender.reportManualScanLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        logcat(WARN) { "PIR-SCAN: onTrimMemory called with level: $level" }
    }

    override fun onDestroy() {
        logcat { "PIR-SCAN: PIR service destroyed" }
        pirJobsRunner.stop()
    }

    companion object {
        private const val PIR_SCAN_NOTIFICATION_ID = 8791
    }
}
