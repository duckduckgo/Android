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

package com.duckduckgo.pir.internal.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.pir.internal.scan.PirScan
import dagger.android.AndroidInjection
import javax.inject.Inject
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat

@InjectWith(scope = ServiceScope::class)
class PirScheduledService : Service() {
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
        logcat { "PIR-SCHEDULED: PIR service started on ${Process.myPid()} thread: ${Thread.currentThread().name}" }
        pirScan.executeAllBrokers(this) {
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        logcat { "PIR-SCHEDULED: PIR service destroyed" }
        pirScan.stop()
    }
}
