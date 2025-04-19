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

package com.duckduckgo.remote.messaging.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RemoteMessagesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(messageEntity: RemoteMessageEntity)

    @Query("select * from remote_message where id = :id")
    abstract fun messagesById(id: String): RemoteMessageEntity?

    @Query("select * from remote_message where status = \"DISMISSED\"")
    abstract fun dismissedMessages(): List<RemoteMessageEntity>

    @Query("update remote_message set status = :newState where id = :id")
    abstract fun updateState(id: String, newState: Status)

    @Query("select * from remote_message where status = \"SCHEDULED\"")
    abstract fun message(): RemoteMessageEntity?

    @Query("select * from remote_message where status = \"SCHEDULED\"")
    abstract fun messagesFlow(): Flow<RemoteMessageEntity?>

    @Query("DELETE FROM remote_message WHERE status = \"DONE\" AND shown = 0")
    abstract fun deleteDoneMessagesNotShown()

    @Query("update remote_message set status = \"DONE\" where status = \"SCHEDULED\"")
    abstract fun markAsDonePreviousMessages()

    @Transaction
    open fun updateActiveMessageStateAndDeleteNeverShownMessages() {
        markAsDonePreviousMessages()
        deleteDoneMessagesNotShown()
    }

    @Transaction
    open fun addOrUpdateActiveMessage(remoteMessageEntity: RemoteMessageEntity) {
        if (activeMessage()?.id == remoteMessageEntity.id) {
            // update the message content if it's the same message
            updateMessageContent(remoteMessageEntity.id, remoteMessageEntity.message, remoteMessageEntity.status)
        } else {
            // new active message, move scheduled messages to done
            markAsDonePreviousMessages()
            val messagesById = messagesById(remoteMessageEntity.id)
            if (messagesById == null) {
                insert(remoteMessageEntity)
            } else {
                updateMessageContent(remoteMessageEntity.id, remoteMessageEntity.message, remoteMessageEntity.status)
            }
        }
        // delete any done messages that were not shown
        deleteDoneMessagesNotShown()
    }

    @Query("select * from remote_message where status = \"SCHEDULED\" LIMIT 1")
    abstract fun activeMessage(): RemoteMessageEntity?

    @Query("update remote_message set message = :content, status = :newState where id = :id AND status != \"DISMISSED\"")
    abstract fun updateMessageContent(id: String, content: String, newState: Status)
}
