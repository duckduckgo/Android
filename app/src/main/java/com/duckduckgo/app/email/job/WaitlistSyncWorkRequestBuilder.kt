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

package com.duckduckgo.app.email.job

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.WaitlistCodeNotification
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WaitlistSyncWorkRequestBuilder @Inject constructor() {

    fun appConfigurationWork(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<EmailWaitlistWorker>(1, TimeUnit.MINUTES)
            .setConstraints(networkAvailable())
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.SECONDS)
            .addTag(EMAIL_WAITLIST_SYNC_WORK_TAG)
            .build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val EMAIL_WAITLIST_SYNC_WORK_TAG = "EmailWaitlistWorker"
    }
}

class EmailWaitlistWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var emailService: EmailService

    @Inject
    lateinit var emailDataStore: EmailDataStore

    @Inject
    lateinit var notification: WaitlistCodeNotification

    @Inject
    lateinit var factory: NotificationFactory

    @Inject
    lateinit var notificationDao: NotificationDao

    @Inject
    lateinit var manager: NotificationManagerCompat

    override suspend fun doWork(): Result {
        if (emailDataStore.inviteCode != null) {
            return Result.success()
        }
        Timber.i("Running email waitlist sync")
        return withContext(Dispatchers.IO) {
            return@withContext try {
                Timber.i("Running waitlist status")
                val statusResponse = emailService.waitlistStatus()

                if (emailDataStore.waitlistTimestamp > statusResponse.timestamp) {
                    val codeResponse = emailService.getCode(emailDataStore.waitlistToken!!)
                    Timber.i("Running waitlist getcode response is ${codeResponse.code}")
                    emailDataStore.inviteCode = codeResponse.code
                    sendNotification()
                } else {
                    Result.failure()
                }
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    private suspend fun sendNotification(): Result {
        if (!notification.canShow()) {
            Timber.v("Notification no longer showable")
            return Result.success()
        }

        val specification = notification.buildSpecification()
        val launchIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
        val cancelIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)
        val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
        notificationDao.insert(Notification(notification.id))
        manager.notify(specification.systemId, systemNotification)
        return Result.success()
    }

}

@ContributesMultibinding(AppObjectGraph::class)
class AppConfigurationWorkerInjectorPlugin @Inject constructor(
    private val notificationManagerCompat: NotificationManagerCompat,
    private val notificationDao: NotificationDao,
    private val notificationFactory: NotificationFactory,
    private val waitlistCodeNotification: WaitlistCodeNotification,
    private val emailService: EmailService,
    private val emailDataStore: EmailDataStore
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is EmailWaitlistWorker) {
            worker.manager = notificationManagerCompat
            worker.notificationDao = notificationDao
            worker.factory = notificationFactory
            worker.notification = waitlistCodeNotification
            worker.emailDataStore = emailDataStore
            worker.emailService = emailService
            return true
        }
        return false
    }
}
