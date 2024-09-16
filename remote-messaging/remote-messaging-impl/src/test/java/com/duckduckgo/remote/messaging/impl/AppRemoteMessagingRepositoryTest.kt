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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.Share
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Placeholder.ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.MAC_AND_WINDOWS
import com.duckduckgo.remote.messaging.api.Content.PromoSingleAction
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.fixtures.getMessageMapper
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

// TODO: when pattern established, refactor objects to use (create module https://app.asana.com/0/0/1201807285420697/f)
@RunWith(AndroidJUnit4::class)
class AppRemoteMessagingRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val db = Room.inMemoryDatabaseBuilder(context, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val dao = db.remoteMessagesDao()

    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository = mock()

    private val testee = AppRemoteMessagingRepository(
        remoteMessagingConfigRepository,
        dao,
        coroutineRule.testDispatcherProvider,
        getMessageMapper(),
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddMediumMessageThenMessageStored() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = Medium(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = Medium(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                        placeholder = ANNOUNCE,
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList(),
                ),
                message,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddSmallMessageThenMessageStored() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = Small(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = Small(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList(),
                ),
                message,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddBigSingleActionMessageThenMessageStored() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = BigSingleAction(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
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
                        primaryActionText = "actionText",
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList(),
                ),
                message,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddBigTwoActionMessageThenMessageStored() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss,
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
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
                        secondaryAction = Action.Dismiss,
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList(),
                ),
                message,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenAddPromoSingleActionMessageThenMessageStored() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = PromoSingleAction(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = MAC_AND_WINDOWS,
                    action = Share(value = "com.duckduckgo.com", additionalParameters = mapOf("title" to "share title")),
                    actionText = "actionText",
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
        )

        testee.messageFlow().test {
            val message = awaitItem()

            assertEquals(
                RemoteMessage(
                    id = "id",
                    content = PromoSingleAction(
                        titleText = "titleText",
                        descriptionText = "descriptionText",
                        placeholder = MAC_AND_WINDOWS,
                        action = Share(value = "com.duckduckgo.com", additionalParameters = mapOf("title" to "share title")),
                        actionText = "actionText",
                    ),
                    matchingRules = emptyList(),
                    exclusionRules = emptyList(),
                ),
                message,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenDismissMessageThenUpdateState() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss,
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
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
    fun whenGetDismissedMessagesThenReturnDismissedMessageIds() = runTest {
        testee.activeMessage(
            RemoteMessage(
                id = "id",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss,
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
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
                status = Status.SCHEDULED,
            ),
        )

        testee.activeMessage(
            RemoteMessage(
                id = "id2",
                content = BigTwoActions(
                    titleText = "titleText",
                    descriptionText = "descriptionText",
                    placeholder = ANNOUNCE,
                    primaryAction = Action.PlayStore(value = "com.duckduckgo.com"),
                    primaryActionText = "actionText",
                    secondaryActionText = "actionText",
                    secondaryAction = Action.Dismiss,
                ),
                matchingRules = emptyList(),
                exclusionRules = emptyList(),
            ),
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
