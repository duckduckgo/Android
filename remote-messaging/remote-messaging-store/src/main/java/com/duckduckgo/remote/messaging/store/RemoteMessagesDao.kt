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
    abstract fun messagesFlow(): Flow<RemoteMessageEntity?>

    @Query("DELETE FROM remote_message WHERE status = \"SCHEDULED\"")
    abstract fun deleteActiveMessages()

    @Transaction
    open fun newMessage(messageEntity: RemoteMessageEntity) {
        deleteActiveMessages()
        insert(messageEntity)
    }
}
