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

package com.duckduckgo.sync.impl.auditlog

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.engine.SyncEngineLifecycle
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

/** One system notification for every unseen login by another device. */
interface NewLoginNotifier {
    /**
     * Posts the notification when [result] added logins to the pending list. If it is showing, it is replaced, not added again,
     * and it alerts again. A late device name can change the device count: that updates a showing notification without an alert.
     */
    suspend fun onCheckFinished(result: AuditLogCheckResult)

    suspend fun cancel()
}

@ContributesBinding(scope = AppScope::class, binding = binding<NewLoginNotifier>())
@ContributesIntoSet(scope = AppScope::class, binding = binding<SyncEngineLifecycle>())
@SingleIn(AppScope::class)
class RealNewLoginNotifier @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val stateStore: AuditLogCheckStateStore,
    private val loginAuditLogFeature: LoginAuditLogFeature,
    private val dispatchers: DispatcherProvider,
) : NewLoginNotifier, SyncEngineLifecycle {

    override suspend fun onCheckFinished(result: AuditLogCheckResult) {
        if (result !is AuditLogCheckResult.Checked || (result.newLogins == 0 && result.renamedLogins == 0)) {
            logcat(VERBOSE) { "Sync-AuditLog: no new logins or names, new login notification not posted" }
            return
        }
        if (!loginAuditLogFeature.isNotificationEnabled()) {
            logcat { "Sync-AuditLog: notification flag off, new login notification not posted" }
            return
        }

        withContext(dispatchers.io()) {
            val pending = stateStore.read().pendingLogins
            // By name, as the banner counts them: a device that logs in again gets a new ID.
            val deviceCount = pending.map { it.deviceName }.distinct().size
            if (deviceCount == 0) {
                logcat { "Sync-AuditLog: no unseen logins left, new login notification not posted" }
                return@withContext
            }
            val alert = result.newLogins > 0
            if (!alert && !isShowing()) {
                logcat(VERBOSE) { "Sync-AuditLog: late device name, new login notification not showing" }
                return@withContext
            }
            if (!canPost()) {
                logcat { "Sync-AuditLog: notifications are off, new login notification not posted" }
                return@withContext
            }
            notificationManager.checkPermissionAndNotify(context, NEW_LOGIN_NOTIFICATION_ID, buildNotification(deviceCount, alert))
            logcat {
                "Sync-AuditLog: new login notification posted (alert=$alert, new logins=${result.newLogins}, " +
                    "renamed=${result.renamedLogins}, unseen logins=${pending.size})"
            }
        }
    }

    override suspend fun cancel() = withContext(dispatchers.io()) {
        notificationManager.cancel(NEW_LOGIN_NOTIFICATION_ID)
        logcat { "Sync-AuditLog: new login notification cancelled" }
    }

    override fun onSyncEnabled() = Unit

    override fun onSyncDisabled() {
        notificationManager.cancel(NEW_LOGIN_NOTIFICATION_ID)
        logcat { "Sync-AuditLog: new login notification cancelled (Sync turned off)" }
    }

    private fun canPost(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        val channel = notificationManager.getNotificationChannelCompat(SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID)
        return channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    private fun isShowing() = notificationManager.activeNotifications.any { it.id == NEW_LOGIN_NOTIFICATION_ID }

    private fun buildNotification(deviceCount: Int, alert: Boolean): Notification =
        NotificationCompat.Builder(context, SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentTitle(context.resources.getQuantityString(R.plurals.syncNewLoginNotificationTitle, deviceCount))
            .setContentText(context.resources.getQuantityString(R.plurals.syncNewLoginNotificationBody, deviceCount, deviceCount))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(!alert)
            .build()

    internal companion object {
        const val NEW_LOGIN_NOTIFICATION_ID = 9451
        const val SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID = "com.duckduckgo.sync.newLogin"
    }
}
