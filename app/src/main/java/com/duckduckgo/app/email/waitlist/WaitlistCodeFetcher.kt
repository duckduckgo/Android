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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.WorkManager
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.*
import com.duckduckgo.app.email.AppEmailManager.FetchCodeResult.*
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.waitlist.WaitlistWorkRequestBuilder.Companion.EMAIL_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface WaitlistCodeFetcher : LifecycleObserver {
    suspend fun fetchInviteCode()
}

class AppWaitlistCodeFetcher(
    private val workManager: WorkManager,
    private val emailManager: EmailManager,
    private val notification: SchedulableNotification,
    private val notificationSender: NotificationSender,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : WaitlistCodeFetcher {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun executeWaitlistCodeFetcher() {
        appCoroutineScope.launch {
            if (emailManager.waitlistState() == JoinedQueue) {
                fetchInviteCode()
            }
        }
    }

    override suspend fun fetchInviteCode() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            when (emailManager.fetchInviteCode()) {
                CodeExisted -> {
                    workManager.cancelAllWorkByTag(EMAIL_WAITLIST_SYNC_WORK_TAG)
                }
                Code -> {
                    workManager.cancelAllWorkByTag(EMAIL_WAITLIST_SYNC_WORK_TAG)
                    notificationSender.sendNotification(notification)
                }
                NoCode -> {
                    // NOOP
                }
            }
        }
    }
}
