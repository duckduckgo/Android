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

package com.duckduckgo.networkprotection.subscription.notification

import android.app.Notification
import android.content.Context
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.notification.NetPDisabledNotificationBuilder
import com.duckduckgo.networkprotection.impl.notification.RealNetPDisabledNotificationBuilder
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGHEST
import javax.inject.Inject

@ContributesBinding(
    scope = VpnScope::class,
    priority = HIGHEST,
)
class SubsNetpDisabledNotificationBuilder @Inject constructor(
    private val realNetPDisabledNotificationBuilder: RealNetPDisabledNotificationBuilder,
    private val netpRepository: NetworkProtectionRepository,
) : NetPDisabledNotificationBuilder {

    override fun buildDisabledNotification(context: Context): Notification {
        return if (netpRepository.vpnAccessRevoked) {
            buildVpnAccessRevokedNotification(context)
        } else {
            realNetPDisabledNotificationBuilder.buildDisabledNotification(context)
        }
    }

    override fun buildSnoozeNotification(
        context: Context,
        triggerAtMillis: Long,
    ): Notification = realNetPDisabledNotificationBuilder.buildSnoozeNotification(context, triggerAtMillis)

    override fun buildDisabledByVpnNotification(context: Context): Notification =
        realNetPDisabledNotificationBuilder.buildDisabledByVpnNotification(context)
}
