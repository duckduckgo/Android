/*
 * Copyright (c) 2021 DuckDuckGo
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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aCardsListMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.cardsListContent
import com.duckduckgo.remote.messaging.impl.models.*
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohort
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStore
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStoreImpl
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RemoteMessagingConfigMatcherTest {

    @get:org.junit.Rule
    var coroutineRule = CoroutineTestRule()

    private val deviceAttributeMatcher: AttributeMatcherPlugin = mock()
    private val androidAppAttributeMatcher: AttributeMatcherPlugin = mock()
    private val userAttributeMatcher: AttributeMatcherPlugin = mock()
    private val remoteMessagingRepository: RemoteMessagingRepository = mock()
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RemoteMessagingDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val cohortDao = db.remoteMessagingCohortDao()
    private val remoteMessagingCohortStore: RemoteMessagingCohortStore = RemoteMessagingCohortStoreImpl(db, coroutineRule.testDispatcherProvider)

    private val testee = RemoteMessagingConfigMatcher(
        setOf(deviceAttributeMatcher, androidAppAttributeMatcher, userAttributeMatcher),
        remoteMessagingRepository,
        remoteMessagingCohortStore,
    )

    @Test
    fun whenEmptyConfigThenReturnNull() = runBlocking {
        val emptyRemoteConfig = RemoteConfig(messages = emptyList(), rules = emptyList())

        val message = testee.evaluate(emptyRemoteConfig)

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstMessage() = runBlocking {
        val noRulesRemoteConfig = RemoteConfig(messages = listOf(aSmallMessage()), rules = emptyList())

        val message = testee.evaluate(noRulesRemoteConfig)

        assertEquals(aSmallMessage(), message)
    }

    @Test
    fun whenNotExistingRuleThenReturnSkipMessage() = runBlocking {
        val noRulesRemoteConfig = RemoteConfig(
            messages = listOf(
                aSmallMessage(matchingRules = rules(1)),
                aMediumMessage(),
            ),
            rules = emptyList(),
        )

        val message = testee.evaluate(noRulesRemoteConfig)

        assertEquals(aMediumMessage(), message)
    }

    @Test
    fun whenNoMessagesThenReturnNull() = runBlocking {
        val noMessagesRemoteConfig = RemoteConfig(
            messages = emptyList(),
            rules = listOf(rule(id = 1, matchingAttributes = arrayOf(Api(max = 19)))),
        )

        val message = testee.evaluate(noMessagesRemoteConfig)

        assertNull(message)
    }

    @Test
    fun whenDeviceDoesNotMatchMessageRulesThenReturnNull() = runBlocking {
        givenDeviceMatches()

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1)),
                ),
                rules = listOf(rule(id = 1, matchingAttributes = arrayOf(Api(max = 19)))),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstNonExcludedMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(3)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                    rule(id = 3, matchingAttributes = arrayOf(EmailEnabled(value = false))),
                ),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(3)), message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedThenReturnNull() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2))),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                ),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedByOneOfMultipleRulesThenReturnNull() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true), Bookmarks(max = 10))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 3)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 3, 4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(5)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(EmailEnabled(value = true), Bookmarks(max = 10))),
                    rule(id = 3, matchingAttributes = arrayOf(EmailEnabled(value = true), Bookmarks(max = 10))),
                    rule(id = 4, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 5, matchingAttributes = arrayOf(EmailEnabled(value = true))),
                ),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenMultipleMatchingMessagesAndSomeExcludedThenReturnFirstNonExcludedMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = emptyList()),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                ),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1), exclusionRules = emptyList()), message)
    }

    @Test
    fun whenMessageMatchesAndExclusionRuleFailsThenReturnMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(EmailEnabled(value = false))),
                ),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1))),
                rules = listOf(rule(id = 1, matchingAttributes = arrayOf(Api(max = 19)))),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesForMultipleMessagesThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1)),
                    aSmallMessage(matchingRules = rules(1)),
                ),
                rules = listOf(rule(id = 1, matchingAttributes = arrayOf(Api(max = 19)))),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesForOneOfMultipleMessagesThenReturnMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1, 2)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(EmailEnabled(value = false))),
                ),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1, 2)), message)
    }

    @Test
    fun whenUserDismissedMessagesAndDeviceMatchesMultipleMessagesThenReturnFistMatchNotDismissed() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))
        givenUserDismissed("1")
        val rules = listOf(
            rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
        )

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(id = "1", matchingRules = rules(1)),
                    aMediumMessage(id = "2", matchingRules = rules(1)),
                ),
                rules = rules,
            ),
        )

        assertEquals(aMediumMessage(id = "2", matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesAnyRuleThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1, 2))),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        assertEquals(aMediumMessage(matchingRules = rules(1, 2)), message)
    }

    @Test
    fun whenDeviceDoesMatchAnyRuleThenReturnNull() = runBlocking {
        givenDeviceMatches(Locale(value = listOf("en-US")), Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1, 2)),
                    aSmallMessage(matchingRules = rules(1, 2)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 15))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenUnknownRuleFailsThenReturnNull() = runBlocking {
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Unknown(fallback = false))),
                ),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenUnknownRuleMatchesThenReturnFirstMatch() = runBlocking {
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1)),
                ),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Unknown(fallback = true))),
                ),
            ),
        )

        assertEquals(aSmallMessage(matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesAndPartOfPercentileThenReturnMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.1f))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(id = "message1", matchingRules = rules(1))),
                rules = listOf(rule(id = 1, percentile = 0.6f, matchingAttributes = arrayOf(Api(max = 19)))),
            ),
        )

        assertEquals(aMediumMessage(id = "message1", matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesButOutOfPercentileThenReturnNull() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.5f))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(id = "message1", matchingRules = rules(1))),
                rules = listOf(rule(id = 1, percentile = 0.1f, matchingAttributes = arrayOf(Api(max = 19)))),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedAndUserPartOfPercentileThenReturnNull() = runBlocking {
        givenDeviceMatches(Locale(value = listOf("en-US")))
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.1f))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(id = "message1", exclusionRules = rules(2))),
                rules = listOf(
                    rule(id = 2, percentile = 0.5f, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                ),
            ),
        )

        assertNull(message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedButOutOfPercentileThenReturnMessage() = runBlocking {
        givenDeviceMatches(Locale(value = listOf("en-US")))
        cohortDao.insert(RemoteMessagingCohort(messageId = "message1", percentile = 0.5f))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(id = "message1", exclusionRules = rules(2))),
                rules = listOf(
                    rule(id = 2, percentile = 0.1f, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                ),
            ),
        )

        assertEquals(aMediumMessage(id = "message1", exclusionRules = rules(2)), message)
    }

    @Test
    fun whenCardsListMessageWithNoRulesAndCardItemsWithNoRulesThenReturnsMessageWithAllItems() = runBlocking {
        val cardItem1 = CardItem.ListItem(
            id = "item1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Card 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        )
        val cardItem2 = CardItem.ListItem(
            id = "item2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Card 2",
            descriptionText = "Description 2",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        )
        val cardsListContent = cardsListContent(listItems = listOf(cardItem1, cardItem2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = emptyList(),
            ),
        )

        assertEquals(message, result)
    }

    @Test
    fun whenCardsListMessageWithSomeCardItemsPassingRulesThenReturnsFilteredMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val passingItem = CardItem.ListItem(
            id = "passing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Card",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val failingItem1 = CardItem.ListItem(
            id = "failing1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Card 1",
            descriptionText = "Description",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val failingItem2 = CardItem.ListItem(
            id = "failing2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Card 2",
            descriptionText = "Description",
            placeholder = Content.Placeholder.IMAGE_AI,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val cardsListContent = cardsListContent(listItems = listOf(passingItem, failingItem1, failingItem2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        val expectedContent = cardsListContent.copy(listItems = listOf(passingItem))
        assertEquals(message.copy(content = expectedContent), result)
    }

    @Test
    fun whenCardsListMessageWithAllCardItemsFailingRulesThenReturnsNull() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val failingItem1 = CardItem.ListItem(
            id = "failing1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Card 1",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val failingItem2 = CardItem.ListItem(
            id = "failing2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Card 2",
            descriptionText = "Description",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val cardsListContent = cardsListContent(listItems = listOf(failingItem1, failingItem2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun whenCardsListMessageWithCardItemsWithNoRulesThenReturnsAllItems() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val cardItem1 = CardItem.ListItem(
            id = "item1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Card 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        )
        val cardItem2 = CardItem.ListItem(
            id = "item2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Card 2",
            descriptionText = "Description 2",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        )
        val cardsListContent = cardsListContent(listItems = listOf(cardItem1, cardItem2))
        val message = aCardsListMessage(content = cardsListContent, matchingRules = rules(1))

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                ),
            ),
        )

        assertEquals(message, result)
    }

    @Test
    fun whenCardsListMessageWithCardItemMatchingExclusionRuleThenItemFiltered() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))
        val passingItem = CardItem.ListItem(
            id = "passing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Card",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = rules(3),
        )
        val excludedItem = CardItem.ListItem(
            id = "excluded",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Excluded Card",
            descriptionText = "Description",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = rules(2),
        )
        val cardsListContent = cardsListContent(listItems = listOf(passingItem, excludedItem))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Locale(value = listOf("en-US")))),
                    rule(id = 3, matchingAttributes = arrayOf(EmailEnabled(value = true))),
                ),
            ),
        )

        val expectedContent = cardsListContent.copy(listItems = listOf(passingItem))
        assertEquals(message.copy(content = expectedContent), result)
    }

    @Test
    fun whenNonCardsListMessageThenReturnsMessageUnmodified() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val message = aMediumMessage(matchingRules = rules(1))

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                ),
            ),
        )

        assertEquals(message, result)
    }

    @Test
    fun whenCardsListMessageFailsOwnRulesThenReturnsNullWithoutCheckingCardItems() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val cardItem = CardItem.ListItem(
            id = "item1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Card 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val cardsListContent = cardsListContent(listItems = listOf(cardItem))
        val message = aCardsListMessage(content = cardsListContent, matchingRules = rules(2))

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun whenSectionTitleReferencesItemsThatAllPassThenSectionTitleIsKept() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val item1 = CardItem.ListItem(
            id = "item1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Item 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val item2 = CardItem.ListItem(
            id = "item2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Item 2",
            descriptionText = "Description 2",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val sectionTitle = CardItem.SectionTitle(
            id = "section1",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section 1",
            itemIDs = listOf("item1", "item2"),
        )
        val cardsListContent = cardsListContent(listItems = listOf(sectionTitle, item1, item2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                ),
            ),
        )

        assertEquals(message, result)
    }

    @Test
    fun whenSectionTitleReferencesItemsThatAllFailThenSectionTitleIsRemoved() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val passingItem = CardItem.ListItem(
            id = "passing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Item",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val failingItem1 = CardItem.ListItem(
            id = "failing1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val failingItem2 = CardItem.ListItem(
            id = "failing2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item 2",
            descriptionText = "Description 2",
            placeholder = Content.Placeholder.IMAGE_AI,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val sectionTitle = CardItem.SectionTitle(
            id = "section1",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section with failing items",
            itemIDs = listOf("failing1", "failing2"),
        )
        val cardsListContent = cardsListContent(listItems = listOf(passingItem, sectionTitle, failingItem1, failingItem2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        val expectedContent = cardsListContent.copy(listItems = listOf(passingItem))
        assertEquals(message.copy(content = expectedContent), result)
    }

    @Test
    fun whenSectionTitleReferencesSomePassingItemsThenSectionTitleIsKept() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val passingItem = CardItem.ListItem(
            id = "passing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Item",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val failingItem = CardItem.ListItem(
            id = "failing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item",
            descriptionText = "Description",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val sectionTitle = CardItem.SectionTitle(
            id = "section1",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section with mixed items",
            itemIDs = listOf("passing", "failing"),
        )
        val cardsListContent = cardsListContent(listItems = listOf(sectionTitle, passingItem, failingItem))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        val expectedContent = cardsListContent.copy(listItems = listOf(sectionTitle, passingItem))
        assertEquals(message.copy(content = expectedContent), result)
    }

    @Test
    fun whenAllItemsAndSectionTitlesAreFilteredThenReturnsNull() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val failingItem1 = CardItem.ListItem(
            id = "failing1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item 1",
            descriptionText = "Description 1",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val failingItem2 = CardItem.ListItem(
            id = "failing2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item 2",
            descriptionText = "Description 2",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val sectionTitle = CardItem.SectionTitle(
            id = "section1",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section",
            itemIDs = listOf("failing1", "failing2"),
        )
        val cardsListContent = cardsListContent(listItems = listOf(sectionTitle, failingItem1, failingItem2))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun whenMultipleSectionsAndOnlyOneSectionItemsFailThenOnlyThatSectionIsRemoved() = runBlocking {
        givenDeviceMatches(Api(max = 19))
        val passingItem1 = CardItem.ListItem(
            id = "passing1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Item 1",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val passingItem2 = CardItem.ListItem(
            id = "passing2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Passing Item 2",
            descriptionText = "Description",
            placeholder = Content.Placeholder.RADAR,
            primaryAction = Action.Dismiss,
            matchingRules = rules(1),
            exclusionRules = emptyList(),
        )
        val failingItem = CardItem.ListItem(
            id = "failing",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = "Failing Item",
            descriptionText = "Description",
            placeholder = Content.Placeholder.IMAGE_AI,
            primaryAction = Action.Dismiss,
            matchingRules = rules(2),
            exclusionRules = emptyList(),
        )
        val section1 = CardItem.SectionTitle(
            id = "section1",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section 1 - Passing",
            itemIDs = listOf("passing1", "passing2"),
        )
        val section2 = CardItem.SectionTitle(
            id = "section2",
            type = CardItemType.LIST_SECTION_TITLE,
            titleText = "Section 2 - Failing",
            itemIDs = listOf("failing"),
        )
        val cardsListContent = cardsListContent(listItems = listOf(section1, passingItem1, passingItem2, section2, failingItem))
        val message = aCardsListMessage(content = cardsListContent)

        val result = testee.evaluate(
            RemoteConfig(
                messages = listOf(message),
                rules = listOf(
                    rule(id = 1, matchingAttributes = arrayOf(Api(max = 19))),
                    rule(id = 2, matchingAttributes = arrayOf(Api(max = 15))),
                ),
            ),
        )

        val expectedContent = cardsListContent.copy(listItems = listOf(section1, passingItem1, passingItem2))
        assertEquals(message.copy(content = expectedContent), result)
    }

    private suspend fun givenDeviceMatches(
        vararg matchingAttributes: MatchingAttribute,
    ) {
        whenever(deviceAttributeMatcher.evaluate(any())).thenReturn(false)
        whenever(androidAppAttributeMatcher.evaluate(any())).thenReturn(false)
        whenever(userAttributeMatcher.evaluate(any())).thenReturn(false)

        matchingAttributes.forEach {
            whenever(deviceAttributeMatcher.evaluate(it)).thenReturn(true)
            whenever(androidAppAttributeMatcher.evaluate(it)).thenReturn(true)
            whenever(userAttributeMatcher.evaluate(it)).thenReturn(true)
        }
    }

    private fun givenUserDismissed(vararg ids: String) {
        whenever(remoteMessagingRepository.dismissedMessages()).thenReturn(ids.asList())
    }

    private fun rule(
        id: Int,
        percentile: Float = 1f,
        vararg matchingAttributes: MatchingAttribute,
    ) = Rule(id, TargetPercentile(before = percentile), matchingAttributes.asList())

    private fun rules(vararg ids: Int) = ids.asList()
}
