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

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import logcat.logcat

@ContributesWorker(AppScope::class)
class PirScheduledScanWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        logcat { "PIR-WORKED: doRemoteWork ${Process.myPid()}" }
        context.applicationContext.startService(Intent(context, PirScheduledService::class.java))
        return Result.success()
    }

    companion object {
        const val TAG_SCHEDULED_SCAN = "TAG-PIR-SCHEDULED-SCAN"
    }
}
