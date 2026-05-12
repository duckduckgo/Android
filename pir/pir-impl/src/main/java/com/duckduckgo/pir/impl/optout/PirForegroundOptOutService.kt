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

package com.duckduckgo.pir.impl.optout

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
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@InjectWith(scope = ServiceScope::class)
class PirForegroundOptOutService : Service(), CoroutineScope by MainScope() {
    @Inject
    lateinit var pirOptOut: PirOptOut

    @Inject
    lateinit var pirNotificationManager: PirNotificationManager

    @Inject
    lateinit var pirWorkHandler: PirWorkHandler

    @Inject
    lateinit var pirFeatureDataCleaner: PirFeatureDataCleaner

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
        logcat { "PIR-OPT-OUT: PIR service started on ${Process.myPid()} thread: ${Thread.currentThread().name}" }
        val notification: Notification = pirNotificationManager.createScanStatusNotification(
            title = getString(R.string.pirFeatureName),
            message = getString(
                R.string.pirOptOutNotificationMessageInProgress,
            ),
        )

        try {
            ServiceCompat.startForeground(
                this,
                PIR_OPT_OUT_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) {
                    FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (_: Exception) {
            logcat(LogPriority.ERROR) { "PIR-OPT-OUT: Could not start the service as foreground!" }
            // If we can't start as a foreground service, there's no point in continuing.
            stopSelf()
            return START_NOT_STICKY
        }

        launch {
            if (pirWorkHandler.canRunPir().firstOrNull() == false) {
                logcat { "PIR-OPT-OUT: PIR opt-out not allowed to run!" }
                pirWorkHandler.cancelWork()
                pirFeatureDataCleaner.removeAllData()
                stopSelf()
                return@launch
            }

            val brokers = intent?.getStringExtra(EXTRA_BROKER_TO_OPT_OUT)

            val result = if (!brokers.isNullOrEmpty()) {
                pirOptOut.execute(listOf(brokers), this@PirForegroundOptOutService)
            } else {
                pirOptOut.executeForBrokersWithRecords(this@PirForegroundOptOutService)
            }

            if (result.isSuccess) {
                pirNotificationManager.showScanStatusNotification(
                    title = getString(R.string.pirFeatureName),
                    message = getString(R.string.pirOptOutNotificationMessageComplete),
                )
            }
            stopSelf()
        }

        logcat { "PIR-OPT-OUT: START_NOT_STICKY" }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logcat { "PIR-OPT-OUT: PIR service destroyed" }
        pirOptOut.stop()
    }

    companion object {
        private const val PIR_OPT_OUT_NOTIFICATION_ID = 9782
        const val EXTRA_BROKER_TO_OPT_OUT = "EXTRA_BROKER_TO_OPT_OUT"
    }
}
