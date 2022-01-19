/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.ActionType
import com.duckduckgo.remote.messaging.api.Action.Dismiss
import com.duckduckgo.remote.messaging.api.Action.PlayStore
import com.duckduckgo.remote.messaging.api.Action.Url
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status.SCHEDULED
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AppRemoteMessagingRepository(
    private val remoteMessagesDao: RemoteMessagesDao
) : RemoteMessagingRepository {

    private val messageMapper = MessageMapper()

    override fun add(message: RemoteMessage) {
        val stringMessage = messageMapper.toString(message)
        remoteMessagesDao.insert(RemoteMessageEntity(id = message.id, message = stringMessage, status = SCHEDULED))
    }

    override fun message(): RemoteMessage? {
        val messageEntity = remoteMessagesDao.messages().firstOrNull() ?: return null
        val message = messageMapper.fromMessage(messageEntity.message) ?: return null

        return RemoteMessage(message.id, message.content, emptyList(), emptyList())
    }

    private class MessageMapper {

        fun toString(sitePayload: RemoteMessage): String {
            return messageAdapter.toJson(sitePayload)
        }

        fun fromMessage(payload: String): RemoteMessage? {
            return runCatching {
                messageAdapter.fromJson(payload)
            }.getOrNull()
        }

        companion object {
            var moshi = Moshi.Builder()
                .add(
                    PolymorphicJsonAdapterFactory.of(Content::class.java, "messageType")
                        .withSubtype(Small::class.java, MessageType.SMALL.name)
                        .withSubtype(Medium::class.java, MessageType.MEDIUM.name)
                        .withSubtype(BigSingleAction::class.java, MessageType.BIG_SINGLE_ACTION.name)
                        .withSubtype(BigTwoActions::class.java, MessageType.BIG_TWO_ACTION.name)
                )
                .add(
                    PolymorphicJsonAdapterFactory.of(Action::class.java, "actionType")
                        .withSubtype(PlayStore::class.java, ActionType.PLAYSTORE.name)
                        .withSubtype(Url::class.java, ActionType.URL.name)
                        .withSubtype(Dismiss::class.java, ActionType.DISMISS.name)
                )
                // if you have more adapters, add them before this line:
                .add(KotlinJsonAdapterFactory())
                .build()
            val messageAdapter: JsonAdapter<RemoteMessage> = moshi.adapter(RemoteMessage::class.java)
        }
    }
}
