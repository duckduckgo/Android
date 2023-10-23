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

package com.duckduckgo.networkprotection.impl.waitlist

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.*
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.notification.TaskStackBuilderFactory
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationSpec
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WAITLIST_NOTIFICATION_LAUNCHED
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_WAITLIST_NOTIFICATION_LAUNCHED_DAILY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistInvitedActivity.Companion.NetPWaitlistInvitedScreenWithOriginPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// FIXME open class just to avoid busy work for testing. This class is temporary anyways and will be removed when waitlist is closed
open class NetPWaitlistCodeNotification @Inject constructor(
    private val context: Context,
    private val netPWaitlistManager: NetPWaitlistManager,
    private val notificationSender: Provider<NotificationSender>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val networkProtectionState: NetworkProtectionState,
    private val notificationManager: NotificationManagerCompat,
    private val dispatcherProvider: DispatcherProvider,
) : SchedulableNotification {
    override val id: String = "com.duckduckgo.netp.waitlist"

    override suspend fun canShow(): Boolean {
        return (netPWaitlistManager.getStateSync() is InBeta) && !networkProtectionState.isOnboarded()
    }

    override suspend fun buildSpecification(): NotificationSpec {
        return NetPWaitlistCodeNotificationSpec(context)
    }

    internal fun cancelNotification() {
        coroutineScope.launch(dispatcherProvider.io()) {
            notificationManager.cancel(buildSpecification().systemId)
        }
    }

    internal fun sendNotification() {
        coroutineScope.launch(dispatcherProvider.io()) {
            notificationSender.get().sendNotification(this@NetPWaitlistCodeNotification)
        }
    }
}

private class NetPWaitlistCodeNotificationSpec(
    context: Context,
) : NotificationSpec {
    override val channel: Channel = Channel(
        "com.duckduckgo.netp",
        R.string.netpNotificationChannel,
        NotificationManagerCompat.IMPORTANCE_HIGH,
    )
    override val systemId: Int = 222
    override val name: String = context.getString(R.string.netpNotificationTitle)
    override val icon: Int = com.duckduckgo.mobile.android.R.drawable.notification_logo
    override val title: String = context.getString(R.string.netpNotificationTitle)
    override val description: String = context.getString(R.string.netpNotificationByline)
    override val launchButton: String? = null
    override val closeButton: String? = null
    override val pixelSuffix: String = ""
    override val autoCancel: Boolean = true
    override val bundle: Bundle = Bundle()
    override val color: Int = com.duckduckgo.mobile.android.R.color.ic_launcher_red_background
}

@ContributesMultibinding(AppScope::class)
class NetPWaitlistNotificationPlugin @Inject constructor(
    private val context: Context,
    private val schedulableNotification: NetPWaitlistCodeNotification,
    private val taskStackBuilderFactory: TaskStackBuilderFactory,
    private val pixels: NetworkProtectionPixels,
    private val globalActivityStarter: GlobalActivityStarter,
) : SchedulableNotificationPlugin {

    override fun getSchedulableNotification(): SchedulableNotification {
        return schedulableNotification
    }

    override fun getSpecification(): NotificationSpec {
        return NetPWaitlistCodeNotificationSpec(context)
    }

    override fun onNotificationCancelled() {
        pixels.waitlistNotificationCancelled()
    }

    override fun onNotificationShown() {
        pixels.waitlistNotificationShown()
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = globalActivityStarter.startIntent(
            context,
            NetPWaitlistInvitedScreenWithOriginPixels(
                listOf(NETP_WAITLIST_NOTIFICATION_LAUNCHED_DAILY.pixelName, NETP_WAITLIST_NOTIFICATION_LAUNCHED.pixelName),
            ),
        )
        val pendingIntent: PendingIntent? = taskStackBuilderFactory.createTaskBuilder().run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return pendingIntent
    }
}
