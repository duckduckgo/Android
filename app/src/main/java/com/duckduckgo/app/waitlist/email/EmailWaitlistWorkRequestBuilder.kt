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

package com.duckduckgo.app.waitlist.email

import android.content.Context
import androidx.work.*
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.EmailWaitlistCodeNotification
import com.duckduckgo.di.scopes.AppScope
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class EmailWaitlistWorkRequestBuilder @Inject constructor() {

    fun waitlistRequestWork(withBigDelay: Boolean = true): OneTimeWorkRequest {
        val requestBuilder = OneTimeWorkRequestBuilder<EmailWaitlistWorker>()
            .setConstraints(networkAvailable())
            .addTag(EMAIL_WAITLIST_SYNC_WORK_TAG)

        if (withBigDelay) {
            requestBuilder.setInitialDelay(1, TimeUnit.DAYS)
        } else {
            requestBuilder.setInitialDelay(5, TimeUnit.MINUTES)
        }

        return requestBuilder.build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val EMAIL_WAITLIST_SYNC_WORK_TAG = "EmailWaitlistWorker"
    }
}

@ContributesWorker(AppScope::class)
class EmailWaitlistWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var emailManager: EmailManager

    @Inject
    lateinit var notificationSender: NotificationSender

    @Inject
    lateinit var notification: EmailWaitlistCodeNotification

    @Inject
    lateinit var emailWaitlistWorkRequestBuilder: EmailWaitlistWorkRequestBuilder

    override suspend fun doWork(): Result {

        when (emailManager.fetchInviteCode()) {
            AppEmailManager.FetchCodeResult.CodeExisted -> Result.success()
            AppEmailManager.FetchCodeResult.Code -> notificationSender.sendNotification(notification)
            AppEmailManager.FetchCodeResult.NoCode -> WorkManager.getInstance(context).enqueue(emailWaitlistWorkRequestBuilder.waitlistRequestWork())
        }

        return Result.success()
    }
}
