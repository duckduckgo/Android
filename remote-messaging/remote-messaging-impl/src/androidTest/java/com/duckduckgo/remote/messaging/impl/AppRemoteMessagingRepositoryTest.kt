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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Test

class AppRemoteMessagingRepositoryTest {

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val db = Room.inMemoryDatabaseBuilder(context, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val testee = AppRemoteMessagingRepository(db.remoteMessagesDao())

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddMessageThenMessageStored() {
        testee.add(
            RemoteMessage(
                id = "id",
                messageType = "nothing",
                content = Medium(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        val message = testee.message()

        assertEquals(
            RemoteMessage(
                id = "id",
                messageType = "nothing",
                content = Medium(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            ),
            message
        )
    }
}
