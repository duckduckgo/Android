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

package com.duckduckgo.networkprotection.impl.alerts.reconnect

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.alerts.NetPAlertNotiticationBuilder
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealNetPReconnectNotifications @Inject constructor(
    private val netPAlertNotiticationBuilder: NetPAlertNotiticationBuilder,
    private val notificationManager: NotificationManagerCompat,
) : NetPReconnectNotifications {
    override fun clearNotifications() {
        notificationManager.cancel(NETP_RECONNECT_NOTIFICATION_ID)
    }

    override fun launchReconnectingNotification(context: Context) {
        netPAlertNotiticationBuilder.buildReconnectingNotification(context).also {
            notificationManager.notify(NETP_RECONNECT_NOTIFICATION_ID, it)
        }
    }

    override fun launchReconnectedNotification(context: Context) {
        netPAlertNotiticationBuilder.buildReconnectedNotification(context).also {
            notificationManager.notify(NETP_RECONNECT_NOTIFICATION_ID, it)
        }
    }

    override fun launchReconnectionFailedNotification(context: Context) {
        netPAlertNotiticationBuilder.buildReconnectionFailedNotification(context).also {
            notificationManager.notify(NETP_RECONNECT_NOTIFICATION_ID, it)
        }
    }

    companion object {
        const val NETP_RECONNECT_NOTIFICATION_ID = 9991
    }
}
