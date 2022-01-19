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

import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.ContentTest.MediumTest
import com.duckduckgo.remote.messaging.impl.ContentTest.SmallTest
import com.duckduckgo.remote.messaging.impl.MessageType.medium
import com.duckduckgo.remote.messaging.impl.MessageType.small
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status.SCHEDULED
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AppRemoteMessagingRepository(
    private val remoteMessagesDao: RemoteMessagesDao
) : RemoteMessagingRepository {

    private val messageMapper = MessageMapper()

    override fun add(message: RemoteMessage) {
        val messageContent = when (val content = message.content) {
            is Small -> SmallTest(content.titleText, content.descriptionText)
            is Medium -> MediumTest(content.titleText, content.descriptionText, content.placeholder)
            else -> TODO()
        }
        val stringMessage = messageMapper.toString(RemoteMessageTest(message.id, message.messageType, messageContent))
        remoteMessagesDao.insert(RemoteMessageEntity(id = message.id, message = stringMessage, status = SCHEDULED))
    }

    override fun message(): RemoteMessage? {
        val messageEntity = remoteMessagesDao.messages().firstOrNull() ?: return null
        val message = messageMapper.fromMessage(messageEntity.message) ?: return null

        val messageContent = when (val content = message.content) {
            is SmallTest -> Small(content.titleText, content.descriptionText)
            is MediumTest -> Medium(content.titleText, content.descriptionText, content.placeholder)
            else -> TODO()
        }

        return RemoteMessage(message.id, "nothing", messageContent, emptyList(), emptyList())
    }

    private class MessageMapper {

        fun toString(sitePayload: RemoteMessageTest): String {
            return messageAdapter.toJson(sitePayload)
        }

        fun fromMessage(payload: String): RemoteMessageTest? {
            return runCatching {
                messageAdapter.fromJson(payload)
            }.getOrNull()
        }

        companion object {
            var moshi = Moshi.Builder()
                .add(
                    PolymorphicJsonAdapterFactory.of(ContentTest::class.java, "messageType")
                        .withSubtype(SmallTest::class.java, MessageType.small.name)
                        .withSubtype(MediumTest::class.java, MessageType.medium.name)
                )
                // if you have more adapters, add them before this line:
                .add(KotlinJsonAdapterFactory())
                .build()
            val messageAdapter: JsonAdapter<RemoteMessageTest> = moshi.adapter(RemoteMessageTest::class.java)
        }
    }
}

data class RemoteMessageTest(
    val id: String,
    val messageType: String,
    val content: ContentTest
)

enum class MessageType {
    small,
    medium
}

sealed class ContentTest(@Json(name = "messageType") val messageType: MessageType) {
    data class SmallTest(val titleText: String, val descriptionText: String) : ContentTest(small)
    data class MediumTest(val titleText: String, val descriptionText: String, val placeholder: String) : ContentTest(medium)
}

sealed class Action {
    data class Url(val value: String) : Action()
    data class PlayStore(val value: String) : Action()
    object Dismiss : Action()
}
