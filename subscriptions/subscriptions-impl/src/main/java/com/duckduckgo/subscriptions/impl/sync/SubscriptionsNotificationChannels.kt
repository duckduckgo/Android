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

package com.duckduckgo.subscriptions.impl.sync

import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.sync.SubscriptionNotificationChannelType.SUBSCRIPTION_CHANNEL
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SubscriptionsNotificationChannels @Inject constructor() : NotificationPlugin {
    override fun getChannels(): List<Channel> {
        return listOf(
            SUBSCRIPTION_CHANNEL,
        )
    }
}

internal object SubscriptionNotificationChannelType {
    val SUBSCRIPTION_CHANNEL = Channel(
        "com.duckduckgo.subscriptions",
        R.string.notificationChannelSubscriptions,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
    )
}
