/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist.ui

import android.content.Context
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistWorkRequestBuilder.Companion.MACOS_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistCodeNotification
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistManager
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface MacOsWaitlistWorkRequestBuilder {
    fun waitlistRequestWork(withBigDelay: Boolean = true): OneTimeWorkRequest

    companion object {
        const val MACOS_WAITLIST_SYNC_WORK_TAG = "MacOsWaitlistWorker"
    }
}

@ContributesBinding(AppScope::class)
class RealMacOsWaitlistWorkRequestBuilder @Inject constructor() : MacOsWaitlistWorkRequestBuilder {

    override fun waitlistRequestWork(withBigDelay: Boolean): OneTimeWorkRequest {
        val requestBuilder = OneTimeWorkRequestBuilder<MacOsWaitlistWorker>()
            .setConstraints(networkAvailable())
            .addTag(MACOS_WAITLIST_SYNC_WORK_TAG)

        if (withBigDelay) {
            requestBuilder.setInitialDelay(1, TimeUnit.DAYS)
        } else {
            requestBuilder.setInitialDelay(5, TimeUnit.MINUTES)
        }

        return requestBuilder.build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

class MacOsWaitlistWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var waitlistManager: MacOsWaitlistManager

    @Inject
    lateinit var notificationSender: NotificationSender

    @Inject
    lateinit var notification: SchedulableNotification

    @Inject
    lateinit var workRequestBuilder: MacOsWaitlistWorkRequestBuilder

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
class MacOsWaitlistWorkerInjectorPlugin @Inject constructor(
    private val waitlistManager: MacOsWaitlistManager,
    private val notificationSender: NotificationSender,
    private val notification: MacOsWaitlistCodeNotification,
    private val workRequestBuilder: MacOsWaitlistWorkRequestBuilder,
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is MacOsWaitlistWorker) {
            worker.waitlistManager = waitlistManager
            worker.notificationSender = notificationSender
            worker.notification = notification
            worker.workRequestBuilder = workRequestBuilder
            return true
        }
        return false
    }
}
