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
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Test

class AppRemoteMessagingRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val db = Room.inMemoryDatabaseBuilder(context, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val dao = db.remoteMessagesDao()

    private val testee = AppRemoteMessagingRepository(dao)

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddMediumMessageThenMessageStored() {
        testee.add(
            RemoteMessage(
                id = "id",
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

    @Test
    fun whenAddSmallMessageThenMessageStored() {
        testee.add(
            RemoteMessage(
                id = "id",
                content = Small(
                    titleText = "titleText",
                    descriptionText = "descriptionText"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        val message = testee.message()

        assertEquals(
            RemoteMessage(
                id = "id",
                content = Small(
                    titleText = "titleText",
                    descriptionText = "descriptionText"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            ),
            message
        )
    }

    @Test
    fun whenAddBigSingleActionMessageThenMessageStored() {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigSingleAction(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        val message = testee.message()

        assertEquals(
            RemoteMessage(
                id = "id",
                content = BigSingleAction(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            ),
            message
        )
    }

    @Test
    fun whenAddBigTwoActionMessageThenMessageStored() {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        val message = testee.message()

        assertEquals(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            ),
            message
        )
    }

    @Test
    fun whenDismissMessageThenUpdateState() {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        testee.dismissMessage("id")

        val messages = dao.messages()
        assertEquals(Status.DISMISSED, messages.first().status)
    }

    @Test
    fun whenGetDismisedMessagesThenReturnDismissedMessageIds() {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = "placeholder",
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )
        testee.dismissMessage("id")

        val dismissedMessages = testee.dismissedMessages()
        assertEquals("id", dismissedMessages.first())
    }
}
