/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules a local reminder that fires [daysBeforeCancel] days before the active subscription's
 * expiration. The reminder is only enqueued if the user has an active subscription whose expiry
 * leaves a positive delay.
 */
interface SubscriptionExpirationReminderScheduler {
    suspend fun scheduleReminderNotification(daysBeforeCancel: Int)
    fun cancelScheduledNotification()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SubscriptionExpirationReminderSchedulerImpl @Inject constructor(
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
    private val subscriptionsManager: SubscriptionsManager,
) : SubscriptionExpirationReminderScheduler {

    override suspend fun scheduleReminderNotification(daysBeforeCancel: Int) {
        cancelScheduledNotification()

        if (daysBeforeCancel <= 0) return
        if (!notificationManager.areNotificationsEnabled()) return

        val expiresOrRenewsAt = subscriptionsManager.getSubscription()?.expiresOrRenewsAt ?: return
        val delayMillis = expiresOrRenewsAt - TimeUnit.DAYS.toMillis(daysBeforeCancel.toLong()) - System.currentTimeMillis()
        if (delayMillis <= 0) return

        val request = OneTimeWorkRequestBuilder<SubscriptionExpirationReminderWorker>()
            .addTag(EXPIRATION_REMINDER_WORK_TAG)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueue(request)
    }

    override fun cancelScheduledNotification() {
        workManager.cancelAllWorkByTag(EXPIRATION_REMINDER_WORK_TAG)
    }

    companion object {
        const val EXPIRATION_REMINDER_WORK_TAG = "com.duckduckgo.subscriptions.expiration.reminder.schedule"
    }
}
