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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Placeholder.ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

// TODO: when pattern established, refactor objects to use (create module https://app.asana.com/0/0/1201807285420697/f)
@ExperimentalCoroutinesApi
class AppRemoteMessagingRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val db = Room.inMemoryDatabaseBuilder(context, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val dao = db.remoteMessagesDao()

    private val remoteMessagingConfigRepository = mock<RemoteMessagingConfigRepository>()

    private val testee = AppRemoteMessagingRepository(remoteMessagingConfigRepository, dao, coroutineRule.testDispatcherProvider)

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddMediumMessageThenMessageStored() = runTest {
        testee.add(
            RemoteMessage(
                id = "id",
                content = Medium(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = Medium(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                        placeholder = ANNOUNCE
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList()
                ),
                message
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddSmallMessageThenMessageStored() = runTest {
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

        testee.messageFlow().test {
            val message = awaitItem()

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
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddBigSingleActionMessageThenMessageStored() = runTest {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigSingleAction(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText"
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = BigSingleAction(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                        placeholder = ANNOUNCE,
                        primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                        primaryActionText = "actionText"
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList()
                ),
                message
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddBigTwoActionMessageThenMessageStored() = runTest {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = BigTwoActions(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                        placeholder = ANNOUNCE,
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
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDismissMessageThenUpdateState() = runTest {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )
        testee.messageFlow().test {
            var message = awaitItem()
            assertTrue(message?.content is BigTwoActions)

            testee.dismissMessage("id")
            message = awaitItem()
            assertNull(message)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetDismisedMessagesThenReturnDismissedMessageIds() = runTest {
        testee.add(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
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

    @Test
    fun whenNewMessageAddedThenPreviousNonDismissedMessagesRemoved() = runTest {
        dao.insert(
            RemoteMessageEntity(
                id = "id",
                message = "",
                status = Status.SCHEDULED
            )
        )

        testee.add(
            RemoteMessage(
                id = "id2",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss()
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList()
            )
        )

        testee.messageFlow().test {
            var message = awaitItem()
            assertEquals("id2", message?.id)
            testee.dismissMessage("id2")

            message = awaitItem()
            assertNull(message)
            cancelAndConsumeRemainingEvents()
        }
    }
}
