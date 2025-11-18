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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.mappers.MessageMapper
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status.SCHEDULED
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppRemoteMessagingRepository(
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
    private val remoteMessagesDao: RemoteMessagesDao,
    private val dispatchers: DispatcherProvider,
    private val messageMapper: MessageMapper,
) : RemoteMessagingRepository {

    override fun getMessageById(id: String): RemoteMessage? {
        return remoteMessagesDao.messagesById(id)?.let {
            messageMapper.fromMessage(it.message)
        }
    }

    override fun activeMessage(message: RemoteMessage?) {
        if (message == null) {
            remoteMessagesDao.updateActiveMessageStateAndDeleteNeverShownMessages()
        } else {
            val stringMessage = messageMapper.toString(message)
            remoteMessagesDao.addOrUpdateActiveMessage(RemoteMessageEntity(id = message.id, message = stringMessage, status = SCHEDULED))
        }
    }

    override fun didShow(id: String) = remoteMessagesDao.messagesById(id)?.shown ?: false

    override fun markAsShown(remoteMessage: RemoteMessage) {
        val message = remoteMessagesDao.messagesById(remoteMessage.id) ?: return
        remoteMessagesDao.insert(message.copy(shown = true))
    }

    override fun message(): RemoteMessage? {
        val message = remoteMessagesDao.message()
        if (message == null || message.message.isEmpty()) return null

        val remoteMessage = messageMapper.fromMessage(message.message) ?: return null
        RemoteMessage(
            id = message.id,
            content = remoteMessage.content,
            emptyList(),
            emptyList(),
            emptyList(),
        )
        return remoteMessage
    }

    override fun messageFlow(): Flow<RemoteMessage?> {
        return remoteMessagesDao.messagesFlow().distinctUntilChanged().map {
            if (it == null || it.message.isEmpty()) return@map null

            val message = messageMapper.fromMessage(it.message) ?: return@map null
            RemoteMessage(
                id = it.id,
                content = message.content,
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    override suspend fun dismissMessage(id: String) {
        withContext(dispatchers.io()) {
            remoteMessagesDao.updateState(id, Status.DISMISSED)
            remoteMessagingConfigRepository.invalidate()
        }
    }

    override fun dismissedMessages(): List<String> {
        return remoteMessagesDao.dismissedMessages().map { it.id }.toList()
    }
}
