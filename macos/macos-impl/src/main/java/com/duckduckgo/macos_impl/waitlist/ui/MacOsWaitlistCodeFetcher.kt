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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.work.WorkManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.macos_api.MacOsWaitlistState
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.macos_impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.macos_impl.waitlist.MacOsWaitlistManager
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistWorkRequestBuilder.Companion.MACOS_WAITLIST_SYNC_WORK_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface MacOsWaitlistCodeFetcher : LifecycleObserver {
    suspend fun fetchInviteCode()
}

class RealMacOsWaitlistCodeFetcher(
    private val workManager: WorkManager,
    private val macOsWaitlistManager: MacOsWaitlistManager,
    private val notification: SchedulableNotification,
    private val notificationSender: NotificationSender,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope
) : MacOsWaitlistCodeFetcher {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun executeWaitlistCodeFetcher() {
        appCoroutineScope.launch {
            if (macOsWaitlistManager.getState() is MacOsWaitlistState.JoinedWaitlist) {
                fetchInviteCode()
            }
        }
    }

    override suspend fun fetchInviteCode() {
        withContext(dispatcherProvider.io()) {
            when (macOsWaitlistManager.fetchInviteCode()) {
                CodeExisted -> {
                    workManager.cancelAllWorkByTag(MACOS_WAITLIST_SYNC_WORK_TAG)
                }
                Code -> {
                    workManager.cancelAllWorkByTag(MACOS_WAITLIST_SYNC_WORK_TAG)
                    notificationSender.sendNotification(notification)
                }
                NoCode -> {
                    // NOOP
                }
            }
        }
    }
}
