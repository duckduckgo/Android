/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.app.NotificationManager
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class NotificationScheduler {

    fun scheduleClearDataNotification() {
        WorkManager.getInstance().cancelAllWorkByTag(WORK_REQUEST_TAG)

        val request = OneTimeWorkRequestBuilder<ShowClearDataNotification>()
            .addTag(WORK_REQUEST_TAG)
            .setInitialDelay(1, TimeUnit.SECONDS)

        WorkManager.getInstance().enqueue(request.build())
    }

    class ShowClearDataNotification(val context: Context, params: WorkerParameters) : Worker(context, params) {

        override fun doWork(): Result {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val generator = NotificationGenerator(context)
            val specification = NotificationGenerator.NotificationSpecs.autoClear
            val notification = generator.buildNotification(manager, specification)
            manager.notify(specification.id, notification)
            return Result.SUCCESS
        }
    }

    companion object {
        const val WORK_REQUEST_TAG = "com.duckduckgo.notifications"
    }
}