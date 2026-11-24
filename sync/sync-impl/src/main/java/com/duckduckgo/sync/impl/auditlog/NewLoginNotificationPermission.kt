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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationPermission.Step
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NewLoginNotificationPermission {
    /**
     * True when the POST_NOTIFICATIONS permission is granted (Android 13+), app notifications are on
     * and the new login channel is not turned off.
     */
    suspend fun canPost(): Boolean

    /**
     * @param shouldShowRationale the result of `ActivityCompat.shouldShowRequestPermissionRationale(activity, POST_NOTIFICATIONS)`
     */
    suspend fun nextStep(shouldShowRationale: Boolean): Step

    /**
     * The system screen where the user can turn on what [canPost] needs.
     */
    suspend fun settingsIntent(): Intent

    enum class Step {
        NONE,
        REQUEST_PERMISSION,
        OPEN_SETTINGS,
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewLoginNotificationPermission @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val dispatchers: DispatcherProvider,
) : NewLoginNotificationPermission {

    override suspend fun canPost(): Boolean = withContext(dispatchers.io()) {
        hasPermission() && notificationManager.areNotificationsEnabled() && !isChannelOff()
    }

    override suspend fun nextStep(shouldShowRationale: Boolean): Step = withContext(dispatchers.io()) {
        when {
            canPost() -> Step.NONE
            hasPermission() -> Step.OPEN_SETTINGS
            shouldShowRationale -> Step.REQUEST_PERMISSION
            // Without a rationale the dialog was never shown or is blocked after two refusals; Android does not tell them apart.
            // Onboarding already asks, so blocked is likely, and settings works in both cases.
            else -> Step.OPEN_SETTINGS
        }
    }

    override suspend fun settingsIntent(): Intent = withContext(dispatchers.io()) {
        val onlyChannelOff = hasPermission() && notificationManager.areNotificationsEnabled() && isChannelOff()
        val intent = if (onlyChannelOff) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, NEW_LOGIN_CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED

    private fun isChannelOff(): Boolean =
        notificationManager.getNotificationChannelCompat(NEW_LOGIN_CHANNEL_ID)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

    internal companion object {
        const val NEW_LOGIN_CHANNEL_ID = "com.duckduckgo.sync.newLogin"
    }
}
