/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.waitlist.trackerprotection

import android.content.Context
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.AppTPWaitlistCodeNotification
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder.Companion.APP_TP_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.waitlist.FetchCodeResult
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AppTPWaitlistWorkRequestBuilder {
    fun waitlistRequestWork(withBigDelay: Boolean = true): OneTimeWorkRequest

    companion object {
        const val APP_TP_WAITLIST_SYNC_WORK_TAG = "AppTPWaitlistWorker"
    }
}

@ContributesBinding(AppScope::class)
class RealAppTPWaitlistWorkRequestBuilder @Inject constructor() : AppTPWaitlistWorkRequestBuilder {

    override fun waitlistRequestWork(withBigDelay: Boolean): OneTimeWorkRequest {
        val requestBuilder = OneTimeWorkRequestBuilder<AppTPWaitlistWorker>()
            .setConstraints(networkAvailable())
            .addTag(APP_TP_WAITLIST_SYNC_WORK_TAG)

        if (withBigDelay) {
            requestBuilder.setInitialDelay(1, TimeUnit.DAYS)
        } else {
            requestBuilder.setInitialDelay(5, TimeUnit.MINUTES)
        }

        return requestBuilder.build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

class AppTPWaitlistWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var waitlistManager: TrackingProtectionWaitlistManager

    @Inject
    lateinit var notificationSender: NotificationSender

    @Inject
    lateinit var notification: AppTPWaitlistCodeNotification

    @Inject
    lateinit var workRequestBuilder: AppTPWaitlistWorkRequestBuilder

    override suspend fun doWork(): Result {
        when (waitlistManager.fetchInviteCode()) {
            FetchCodeResult.CodeExisted -> Result.success()
            FetchCodeResult.Code -> notificationSender.sendNotification(notification)
            FetchCodeResult.NoCode -> WorkManager.getInstance(context).enqueue(workRequestBuilder.waitlistRequestWork())
        }

        return Result.success()
    }
}

@ContributesMultibinding(AppScope::class)
class AppConfigurationWorkerInjectorPlugin @Inject constructor(
    private val waitlistManager: TrackingProtectionWaitlistManager,
    private val notificationSender: NotificationSender,
    private val notification: AppTPWaitlistCodeNotification,
    private val workRequestBuilder: AppTPWaitlistWorkRequestBuilder,
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is AppTPWaitlistWorker) {
            worker.waitlistManager = waitlistManager
            worker.notificationSender = notificationSender
            worker.notification = notification
            worker.workRequestBuilder = workRequestBuilder
            return true
        }
        return false
    }
}
