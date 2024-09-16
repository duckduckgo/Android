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

package com.duckduckgo.sync.impl

import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.notification.model.Channel
import com.duckduckgo.app.notification.model.NotificationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SYNC_NOTIFICATION_CHANNEL_ID
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SyncNotificationChannelPlugin @Inject constructor() : NotificationPlugin {
    override fun getChannels(): List<Channel> {
        return listOf(
            SyncNotificationChannelType.SYNC_STATE,
        )
    }
}

internal object SyncNotificationChannelType {
    val SYNC_STATE = Channel(
        SYNC_NOTIFICATION_CHANNEL_ID,
        R.string.sync_notification_channel_name,
        NotificationManagerCompat.IMPORTANCE_HIGH,
    )
}
