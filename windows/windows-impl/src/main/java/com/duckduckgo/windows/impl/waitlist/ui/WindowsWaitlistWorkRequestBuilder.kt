/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.windows.impl.waitlist.ui

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistCodeNotification
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistManager
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistWorkRequestBuilder.Companion.WINDOWS_WAITLIST_SYNC_WORK_TAG
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

interface WindowsWaitlistWorkRequestBuilder {
    fun waitlistRequestWork(withBigDelay: Boolean = true): OneTimeWorkRequest

    companion object {
        const val WINDOWS_WAITLIST_SYNC_WORK_TAG = "WindowsWaitlistWorker"
    }
}

@ContributesBinding(AppScope::class)
class RealWindowsWaitlistWorkRequestBuilder @Inject constructor() : WindowsWaitlistWorkRequestBuilder {

    override fun waitlistRequestWork(withBigDelay: Boolean): OneTimeWorkRequest {
        val requestBuilder = OneTimeWorkRequestBuilder<WindowsWaitlistWorker>()
            .setConstraints(networkAvailable())
            .addTag(WINDOWS_WAITLIST_SYNC_WORK_TAG)

        if (withBigDelay) {
            requestBuilder.setInitialDelay(1, TimeUnit.DAYS)
        } else {
            requestBuilder.setInitialDelay(5, TimeUnit.MINUTES)
        }

        return requestBuilder.build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

class WindowsWaitlistWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var waitlistManager: WindowsWaitlistManager

    @Inject
    lateinit var notificationSender: NotificationSender

    @Inject
    lateinit var notification: SchedulableNotification

    @Inject
    lateinit var workRequestBuilder: WindowsWaitlistWorkRequestBuilder

    override suspend fun doWork(): Result {
        when (waitlistManager.fetchInviteCode()) {
            CodeExisted -> Result.success()
            Code -> notificationSender.sendNotification(notification)
            NoCode -> WorkManager.getInstance(context).enqueue(workRequestBuilder.waitlistRequestWork())
        }

        return Result.success()
    }
}

@ContributesMultibinding(AppScope::class)
class WindowsWaitlistWorkerInjectorPlugin @Inject constructor(
    private val waitlistManager: Provider<WindowsWaitlistManager>,
    private val notificationSender: Provider<NotificationSender>,
    private val notification: Provider<WindowsWaitlistCodeNotification>,
    private val workRequestBuilder: Provider<WindowsWaitlistWorkRequestBuilder>,
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is WindowsWaitlistWorker) {
            worker.waitlistManager = waitlistManager.get()
            worker.notificationSender = notificationSender.get()
            worker.notification = notification.get()
            worker.workRequestBuilder = workRequestBuilder.get()
            return true
        }
        return false
    }
}
