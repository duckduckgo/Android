/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.notification

import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Scheduler for VPN reminder notifications during free trial.
 */
interface VpnReminderNotificationScheduler {
    /**
     * Schedules a VPN reminder notification for day 2 of the free trial.
     * Should be called when the free trial starts.
     */
    suspend fun scheduleVpnReminderNotification()

    /**
     * Cancels any scheduled VPN reminder notifications.
     */
    fun cancelScheduledNotification()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class VpnReminderNotificationSchedulerImpl @Inject constructor(
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
) : VpnReminderNotificationScheduler {

    override suspend fun scheduleVpnReminderNotification() {
        cancelScheduledNotification()

        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        val request = OneTimeWorkRequestBuilder<VpnReminderNotificationWorker>()
            .addTag(VPN_REMINDER_WORK_REQUEST_TAG)
            .setInitialDelay(VPN_REMINDER_DELAY_DURATION_IN_DAYS, TimeUnit.DAYS)
            .build()

        workManager.enqueue(request)
    }

    override fun cancelScheduledNotification() {
        workManager.cancelAllWorkByTag(VPN_REMINDER_WORK_REQUEST_TAG)
    }

    companion object {
        const val VPN_REMINDER_WORK_REQUEST_TAG = "com.duckduckgo.subscriptions.vpn.reminder.schedule"
        const val VPN_REMINDER_DELAY_DURATION_IN_DAYS = 2L
    }
}
