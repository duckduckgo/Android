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

import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.windows.api.WindowsWaitlistState
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.Code
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.CodeExisted
import com.duckduckgo.windows.impl.waitlist.FetchCodeResult.NoCode
import com.duckduckgo.windows.impl.waitlist.WindowsWaitlistManager
import com.duckduckgo.windows.impl.waitlist.ui.WindowsWaitlistWorkRequestBuilder.Companion.WINDOWS_WAITLIST_SYNC_WORK_TAG
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class WindowsWaitlistCodeFetcher @Inject constructor(
    private val workManager: WorkManager,
    private val windowsWaitlistManager: WindowsWaitlistManager,
    private val notification: SchedulableNotification,
    private val notificationSender: NotificationSender,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope,
) : LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Event) {
        if (event == Event.ON_START) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                if (windowsWaitlistManager.getState() is WindowsWaitlistState.JoinedWaitlist) {
                    fetchInviteCode()
                }
            }
        }
    }

    private suspend fun fetchInviteCode() {
        when (windowsWaitlistManager.fetchInviteCode()) {
            CodeExisted -> {
                workManager.cancelAllWorkByTag(WINDOWS_WAITLIST_SYNC_WORK_TAG)
            }
            Code -> {
                workManager.cancelAllWorkByTag(WINDOWS_WAITLIST_SYNC_WORK_TAG)
                notificationSender.sendNotification(notification)
            }
            NoCode -> {
                // NOOP
            }
        }
    }
}
