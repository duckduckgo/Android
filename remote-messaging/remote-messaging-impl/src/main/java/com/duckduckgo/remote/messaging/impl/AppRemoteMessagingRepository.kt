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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.mappers.MessageMapper
import com.duckduckgo.remote.messaging.impl.store.RemoteMessageImageStore
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status.SCHEDULED
import com.duckduckgo.remote.messaging.store.RemoteMessagesDao
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

interface RemoteMessagingRepository {
    fun getMessageById(id: String): RemoteMessage?
    fun activeMessage(message: RemoteMessage?)
    fun message(): RemoteMessage?
    fun messageFlow(): Flow<RemoteMessage?>
    suspend fun dismissMessage(id: String)
    fun dismissedMessages(): List<String>
    fun didShow(id: String): Boolean
    fun markAsShown(remoteMessage: RemoteMessage)

    suspend fun getRemoteMessageImageFile(surface: Surface): String?

    suspend fun clearMessageImage(surface: Surface)

    suspend fun getCardItemImageFilePath(itemId: String): String?
}

class AppRemoteMessagingRepository(
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
    private val remoteMessagesDao: RemoteMessagesDao,
    private val messageMapper: MessageMapper,
    private val remoteMessageImageStore: RemoteMessageImageStore,
    private val currentTimeProvider: CurrentTimeProvider,
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
        val messageEntity = remoteMessagesDao.messagesById(remoteMessage.id) ?: return
        // Stamp the first-shown timestamp on the first impression only.
        remoteMessagesDao.insert(
            messageEntity.copy(
                shown = true,
                firstShownDate = messageEntity.firstShownDate ?: currentTimeProvider.currentTimeMillis(),
            ),
        )
    }

    override fun message(): RemoteMessage? {
        val messageEntity = remoteMessagesDao.message()
        if (messageEntity == null || messageEntity.message.isEmpty()) return null

        val remoteMessage = messageMapper.fromMessage(messageEntity.message) ?: return null
        if (remoteMessage.isExpired(messageEntity.firstShownDate)) {
            dismissExpiredMessage(messageEntity.id)
            return null
        }
        return remoteMessage
    }

    override fun messageFlow(): Flow<RemoteMessage?> {
        return remoteMessagesDao.messagesFlow().distinctUntilChanged().map { messageEntity ->
            if (messageEntity == null || messageEntity.message.isEmpty()) return@map null

            val remoteMessage = messageMapper.fromMessage(messageEntity.message) ?: return@map null
            if (remoteMessage.isExpired(messageEntity.firstShownDate)) {
                dismissExpiredMessage(messageEntity.id)
                return@map null
            }
            RemoteMessage(
                id = messageEntity.id,
                content = remoteMessage.content,
                emptyList(),
                emptyList(),
                remoteMessage.surfaces,
                remoteMessage.displayConditions,
            )
        }
    }

    private fun RemoteMessage.isExpired(firstShownDate: Long?): Boolean {
        val threshold = displayConditions?.dismissAfterDaysShown?.takeIf { it > 0 } ?: return false
        val firstShown = firstShownDate ?: return false
        val elapsedDays = TimeUnit.MILLISECONDS.toDays(currentTimeProvider.currentTimeMillis() - firstShown)
        return elapsedDays >= threshold
    }

    private fun dismissExpiredMessage(id: String) {
        remoteMessagesDao.updateState(id, Status.DISMISSED)
        remoteMessagingConfigRepository.invalidate()
    }

    override suspend fun dismissMessage(id: String) {
        remoteMessagesDao.updateState(id, Status.DISMISSED)
        remoteMessagingConfigRepository.invalidate()
    }

    override fun dismissedMessages(): List<String> {
        return remoteMessagesDao.dismissedMessages().map { it.id }.toList()
    }

    override suspend fun getRemoteMessageImageFile(surface: Surface): String? {
        return remoteMessageImageStore.getLocalImageFilePath(surface)
    }

    override suspend fun clearMessageImage(surface: Surface) {
        remoteMessageImageStore.clearStoredImageFile(surface)
    }

    override suspend fun getCardItemImageFilePath(itemId: String): String? {
        return remoteMessageImageStore.getCardItemImageFilePath(itemId)
    }
}
