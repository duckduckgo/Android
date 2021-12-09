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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.WorkManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.waitlist.trackerprotection.AppTPWaitlistWorkRequestBuilder.Companion.APP_TP_WAITLIST_SYNC_WORK_TAG
import com.duckduckgo.mobile.android.vpn.waitlist.FetchCodeResult
import com.duckduckgo.mobile.android.vpn.waitlist.TrackingProtectionWaitlistManager
import com.duckduckgo.mobile.android.vpn.waitlist.WaitlistState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface TrackingProtectionWaitlistCodeFetcher : LifecycleObserver {
    suspend fun fetchInviteCode()
}

class AppTrackingProtectionWaitlistCodeFetcher(
    private val workManager: WorkManager,
    private val manager: TrackingProtectionWaitlistManager,
    private val notification: SchedulableNotification,
    private val notificationSender: NotificationSender,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : TrackingProtectionWaitlistCodeFetcher {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun executeWaitlistCodeFetcher() {
        appCoroutineScope.launch {
            if (manager.waitlistState() is WaitlistState.JoinedQueue) {
                fetchInviteCode()
            }
        }
    }

    override suspend fun fetchInviteCode() {
        withContext(dispatcherProvider.io()) {
            when (manager.fetchInviteCode()) {
                FetchCodeResult.CodeExisted -> {
                    workManager.cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)
                }
                FetchCodeResult.Code -> {
                    workManager.cancelAllWorkByTag(APP_TP_WAITLIST_SYNC_WORK_TAG)
                    notificationSender.sendNotification(notification)
                }
                else -> {}
            }
        }
    }
}
