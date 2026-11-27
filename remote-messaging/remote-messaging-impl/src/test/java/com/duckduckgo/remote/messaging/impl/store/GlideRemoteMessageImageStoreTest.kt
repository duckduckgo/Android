/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.store

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class GlideRemoteMessageImageStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = mock()
    private lateinit var testee: GlideRemoteMessageImageStore

    @Before
    fun setUp() {
        whenever(context.filesDir).thenReturn(tempFolder.root)
        testee = GlideRemoteMessageImageStore(context, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenAnyMessageIsProcessedThenActionableItemImagesAreCleared() = runTest {
        val actionableImage = storedImage(ACTIONABLE_ITEM_IMAGES_DIR, "setup_sync")

        testee.fetchAndStoreImages(aMessage(Content.Small(titleText = "title", descriptionText = "description")))

        assertFalse(actionableImage.exists())
    }

    @Test
    fun whenNoMessageIsActiveThenActionableItemImagesAreCleared() = runTest {
        val actionableImage = storedImage(ACTIONABLE_ITEM_IMAGES_DIR, "setup_sync")

        testee.fetchAndStoreImages(null)

        assertFalse(actionableImage.exists())
    }

    @Test
    fun whenMessageIsNotCardsListThenCardItemImagesAreKept() = runTest {
        val cardImage = storedImage(CARD_ITEM_IMAGES_DIR, "whats_new_item")

        testee.fetchAndStoreImages(aMessage(Content.ActionableItems(titleText = "title", listItems = emptyList())))

        assertTrue(cardImage.exists())
    }

    @Test
    fun whenMessageIsCardsListThenCardItemImagesAreCleared() = runTest {
        val cardImage = storedImage(CARD_ITEM_IMAGES_DIR, "whats_new_item")

        testee.fetchAndStoreImages(aMessage(aCardsList()))

        assertFalse(cardImage.exists())
    }

    @Test
    fun whenItemImageExistsForMessageContentTypeThenPathIsReturned() = runTest {
        val actionableImage = storedImage(ACTIONABLE_ITEM_IMAGES_DIR, "setup_sync")
        val cardImage = storedImage(CARD_ITEM_IMAGES_DIR, "whats_new_item")

        val actionableMessage = aMessage(Content.ActionableItems(titleText = "title", listItems = emptyList()))
        val cardsListMessage = aMessage(aCardsList())

        assertEquals(actionableImage.absolutePath, testee.getItemImageFilePath(actionableMessage, "setup_sync"))
        assertEquals(cardImage.absolutePath, testee.getItemImageFilePath(cardsListMessage, "whats_new_item"))
        assertNull(testee.getItemImageFilePath(actionableMessage, "whats_new_item"))
        assertNull(testee.getItemImageFilePath(cardsListMessage, "setup_sync"))
    }

    @Test
    fun whenMessageContentHasNoItemsThenItemImagePathIsNull() = runTest {
        storedImage(ACTIONABLE_ITEM_IMAGES_DIR, "setup_sync")

        val message = aMessage(Content.Small(titleText = "title", descriptionText = "description"))

        assertNull(testee.getItemImageFilePath(message, "setup_sync"))
    }

    private fun storedImage(
        dir: String,
        itemId: String,
    ): File = File(tempFolder.root, "$dir/$itemId.png").apply {
        parentFile?.mkdirs()
        writeText("image")
    }

    private fun aCardsList() = Content.CardsList(
        titleText = "title",
        descriptionText = "description",
        placeholder = Content.Placeholder.ANNOUNCE,
        primaryActionText = "",
        primaryAction = Action.Dismiss,
        listItems = emptyList(),
    )

    private fun aMessage(content: Content) = RemoteMessage(
        id = "id",
        content = content,
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = listOf(Surface.NEW_TAB_PAGE),
    )

    private companion object {
        const val CARD_ITEM_IMAGES_DIR = "rmf_card_item_images"
        const val ACTIONABLE_ITEM_IMAGES_DIR = "rmf_actionable_item_images"
    }
}
