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

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenWithLaunchPixel
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.R
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class VpnReminderNotification @Inject constructor(
    private val context: Context,
    private val subscriptions: Subscriptions,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotification {

    override val id = "com.duckduckgo.subscriptions.vpn.reminder"

    override suspend fun canShow(): Boolean {
        return withContext(dispatcherProvider.io()) {
            // Only show if user has an active subscription and VPN is not yet enabled
            val subscriptionStatus = subscriptions.getSubscriptionStatus()
            val isSubscriptionActive = subscriptionStatus.isActive()
            val isVpnEnabled = networkProtectionState.isEnabled()

            return@withContext isSubscriptionActive && !isVpnEnabled
        }
    }

    private fun SubscriptionStatus.isActive(): Boolean {
        return when (this) {
            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> true
            else -> false
        }
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return VpnReminderNotificationSpecification(context)
    }
}

class VpnReminderNotificationSpecification(context: Context) : NotificationSpec {

    override val channel = Channel(
        id = "com.duckduckgo.subscriptions.vpn.reminder",
        name = R.string.vpnReminderNotificationChannelName,
        priority = NotificationManagerCompat.IMPORTANCE_DEFAULT,
    )
    override val systemId = NOTIFICATION_SYSTEM_ID
    override val name = "VPN Reminder"
    override val icon = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.vpnReminderNotificationTitle)
    override val description: String = context.getString(R.string.vpnReminderNotificationDescription)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix = PIXEL_SUFFIX
    override val autoCancel = true
    override val bundle: Bundle = Bundle()
    override val color: Int = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)

    companion object {
        private const val NOTIFICATION_SYSTEM_ID = 111
        private const val PIXEL_SUFFIX = "vpn_reminder"
    }
}

@ContributesMultibinding(AppScope::class)
class VpnReminderNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: VpnReminderNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val globalActivityStarter: GlobalActivityStarter,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun onNotificationCancelled() {
        pixel.fire(pixelName(NOTIFICATION_CANCELLED_PIXEL))
    }

    override fun onNotificationShown() {
        pixel.fire(pixelName(NOTIFICATION_SHOWN_PIXEL))
    }

    private fun pixelName(notificationType: String) = "${notificationType}_${getSpecification().pixelSuffix}"

    override fun getSpecification(): NotificationSpec {
        val deferred = coroutineScope.async(dispatcherProvider.io()) {
            schedulableNotification.buildSpecification()
        }
        return runBlocking {
            deferred.await()
        }
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = globalActivityStarter.startIntent(
            context,
            NetworkProtectionManagementScreenWithLaunchPixel(pixelName(NOTIFICATION_LAUNCHED_PIXEL)),
        ) ?: return null
        return taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    companion object {
        private const val NOTIFICATION_SHOWN_PIXEL = "mnot_s"
        private const val NOTIFICATION_CANCELLED_PIXEL = "mnot_c"
        private const val NOTIFICATION_LAUNCHED_PIXEL = "mnot_l"
    }
}
