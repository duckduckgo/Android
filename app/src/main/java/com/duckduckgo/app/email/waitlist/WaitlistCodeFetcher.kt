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

package com.duckduckgo.app.email.waitlist

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.waitlist.WaitlistSyncWorkRequestBuilder.Companion.EMAIL_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationFactory
import com.duckduckgo.app.notification.NotificationHandlerService
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.notification.model.WaitlistCodeNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface WaitlistCodeFetcher : LifecycleObserver {
    suspend fun fetchInviteCode(): ListenableWorker.Result
}

class AppWaitlistCodeFetcher(
    private val context: Context,
    private val emailService: EmailService,
    private val emailDataStore: EmailDataStore,
    private val notification: WaitlistCodeNotification,
    private val factory: NotificationFactory,
    private val notificationDao: NotificationDao,
    private val manager: NotificationManagerCompat,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : WaitlistCodeFetcher {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun configureWaitlistCodeFetcher() {
        appCoroutineScope.launch {
            if (doesCodeAlreadyExist()) {
                return@launch
            } else if (emailDataStore.waitlistTimestamp > -1 && emailDataStore.waitlistToken != null) {
                fetchInviteCode()
            }
        }
    }

    override suspend fun fetchInviteCode(): ListenableWorker.Result {
        Timber.i("Running email waitlist sync")
        val token = emailDataStore.waitlistToken
        val timestamp = emailDataStore.waitlistTimestamp
        if (doesCodeAlreadyExist()) return ListenableWorker.Result.success()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            try {
                Timber.i("Running waitlist status")
                val waitlistTimestamp = emailService.waitlistStatus().timestamp
                if (timestamp > waitlistTimestamp && token != null) {
                    val inviteCode = emailService.getCode(token).code
                    Timber.i("Running waitlist getcode response is $inviteCode")
                    if (inviteCode.isNotEmpty()) {
                        emailDataStore.inviteCode = inviteCode
                        sendNotification()
                        cancelWorker()
                    }
                }
                ListenableWorker.Result.success()
            } catch (e: Exception) {
                ListenableWorker.Result.failure()
            }
        }
        return ListenableWorker.Result.success()
    }

    private fun doesCodeAlreadyExist(): Boolean {
        if (emailDataStore.inviteCode != null) {
            cancelWorker()
            return true
        }
        return false
    }

    private fun cancelWorker() {
        WorkManager.getInstance(context).cancelAllWorkByTag(EMAIL_WAITLIST_SYNC_WORK_TAG)
    }

    private suspend fun sendNotification() {
        if (!notification.canShow()) {
            Timber.v("Notification no longer showable")
            return
        }

        val specification = notification.buildSpecification()
        val launchIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.launchIntent, specification)
        val cancelIntent = NotificationHandlerService.pendingNotificationHandlerIntent(context, notification.cancelIntent, specification)
        val systemNotification = factory.createNotification(specification, launchIntent, cancelIntent)
        notificationDao.insert(Notification(notification.id))
        manager.notify(specification.systemId, systemNotification)
    }
}
