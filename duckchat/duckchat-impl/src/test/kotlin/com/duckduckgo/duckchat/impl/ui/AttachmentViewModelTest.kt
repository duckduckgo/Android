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

package com.duckduckgo.duckchat.impl.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.AttachmentLimits
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ImageLimits
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.LimitsHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AttachmentViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChatInternal: DuckChatInternal = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val limitsHandler: FakeLimitsHandler = FakeLimitsHandler()
    private val modelStateFlow = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(modelStateFlow)
    }
    private val context: android.content.Context = mock<android.content.Context>().also {
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatImageAttachmentLimitPerConversation, 5))
            .thenReturn("Conversation limit reached")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatImageAttachmentLimitPerMessage, 3))
            .thenReturn("Per-message limit reached")
    }

    private lateinit var viewModel: AttachmentViewModel

    @Before
    fun setUp() {
        viewModel = AttachmentViewModel(
            duckChatInternal = duckChatInternal,
            dispatchers = coroutineRule.testDispatcherProvider,
            modelManager = modelManager,
            limitsHandler = limitsHandler,
            context = context,
            appBuildConfig = appBuildConfig,
        )
    }

    @Test
    fun whenModelsListIsEmptyThenSupportsUploadIsTrue() = runTest {
        modelStateFlow.value = ModelState(models = emptyList())

        assertTrue(viewModel.attachmentState.value.supportsUpload)
    }

    @Test
    fun whenSelectedModelSupportsImageUploadAndFeatureEnabledThenSupportsUploadIsTrue() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(true)
        val model = aModel(id = "m1", supportsImageUpload = true)
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        assertTrue(viewModel.attachmentState.value.supportsUpload)
    }

    @Test
    fun whenFeatureDisabledThenSupportsUploadIsFalse() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(false)
        val model = aModel(id = "m1", supportsImageUpload = true)
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        assertFalse(viewModel.attachmentState.value.supportsUpload)
    }

    @Test
    fun whenModelDoesNotSupportImageUploadThenSupportsUploadIsFalse() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(true)
        val model = aModel(id = "m1", supportsImageUpload = false)
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        assertFalse(viewModel.attachmentState.value.supportsUpload)
    }

    @Test
    fun whenNoAttachmentsAndNoLimitsReachedThenIsAtCapacityIsFalse() = runTest {
        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenImageUploadLimitReachedThenIsAtCapacityIsTrue() = runTest {
        limitsHandler.setImageUploadLimitReached(true)

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenAttachmentsAtPerTurnLimitThenIsAtCapacityIsTrue() = runTest {
        val limits = ImageLimits(maxPerTurn = 2, maxPerConversation = 10)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        addImages(2)

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenAttachmentsBelowPerTurnLimitThenIsAtCapacityIsFalse() = runTest {
        val limits = ImageLimits(maxPerTurn = 3, maxPerConversation = 10)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        addImages(2)

        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenInDuckAiModeAndTotalImagesAtConversationLimitThenIsAtCapacityIsTrue() = runTest {
        val limits = ImageLimits(maxPerTurn = 5, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(3)
        addImages(2)

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenNotInDuckAiModeConversationSentIsNotCountedForCapacity() = runTest {
        val limits = ImageLimits(maxPerTurn = 5, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(false)
        limitsHandler.addConversationImagesSent(5)
        addImages(1)

        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenUnderAllLimitsThenImageLimitErrorIsNull() = runTest {
        assertNull(viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenInDuckAiModeAndTotalImagesOverConversationLimitThenConversationErrorShown() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(3)
        addImages(3)

        assertEquals("Conversation limit reached", viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenNotInDuckAiModeAndConversationSentHighThenNoConversationError() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(false)
        limitsHandler.addConversationImagesSent(5)
        addImages(1)

        assertNull(viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenCurrentImagesOverPerTurnLimitThenPerTurnErrorShown() = runTest {
        val limits = ImageLimits(maxPerTurn = 3, maxPerConversation = 20)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        addImages(4)

        assertEquals("Per-message limit reached", viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenImageUploadLimitReachedThenConversationErrorShown() = runTest {
        limitsHandler.setImageUploadLimitReached(true)

        assertEquals("Conversation limit reached", viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenNoImagesThenHasAttachmentsIsFalse() = runTest {
        assertFalse(viewModel.attachmentState.value.hasAttachments)
    }

    @Test
    fun whenImagesAddedThenHasAttachmentsIsTrue() = runTest {
        addImages(1)

        assertTrue(viewModel.attachmentState.value.hasAttachments)
    }

    @Test
    fun whenImageRemovedByIdThenItIsNoLongerInState() = runTest {
        addImages(2)
        val idToRemove = viewModel.attachmentState.value.images[0].id

        viewModel.removeImageAttachment(idToRemove)

        val remaining = viewModel.attachmentState.value.images
        assertEquals(1, remaining.size)
        assertTrue(remaining.none { it.id == idToRemove })
    }

    @Test
    fun whenRemovingNonExistentIdThenListUnchanged() = runTest {
        addImages(2)

        viewModel.removeImageAttachment("nonexistent-id")

        assertEquals(2, viewModel.attachmentState.value.images.size)
    }

    @Test
    fun whenClearAttachmentsCalledThenImagesAreEmpty() = runTest {
        addImages(2)

        viewModel.clearAttachments()

        assertTrue(viewModel.attachmentState.value.images.isEmpty())
    }

    @Test
    fun whenClearAttachmentsForNewChatCalledThenImagesAreEmpty() = runTest {
        addImages(2)

        viewModel.clearAttachmentsForNewChat()

        assertTrue(viewModel.attachmentState.value.images.isEmpty())
    }

    @Test
    fun whenNoImagesThenGetImageAttachmentsReturnsEmptyList() = runTest {
        assertTrue(viewModel.getImageAttachments().isEmpty())
    }

    @Test
    fun whenImagesAddedThenGetImageAttachmentsReturnsThem() = runTest {
        addImages(2)

        assertEquals(2, viewModel.getImageAttachments().size)
    }

    @Test
    fun whenNoImagesThenGetImageAttachmentsJsonReturnsNull() = runTest {
        assertNull(viewModel.getImageAttachmentsJson())
    }

    @Test
    fun whenImagesAddedThenGetImageAttachmentsJsonContainsCorrectFields() = runTest {
        addImages(1, base64 = "abc123", format = "jpeg")

        val json = viewModel.getImageAttachmentsJson()

        assertNotNull(json)
        assertEquals(1, json!!.length())
        val item = json.getJSONObject(0)
        assertEquals("abc123", item.getString("data"))
        assertEquals("jpeg", item.getString("format"))
    }

    @Test
    fun whenDuckAiModeDisabledThenConversationSentNotCountedInTotal() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(false)
        limitsHandler.addConversationImagesSent(4)
        addImages(1)

        assertNull(viewModel.attachmentState.value.imageLimitError)
        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenDuckAiModeEnabledThenConversationSentCountedInTotal() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(4)
        addImages(2)

        assertNotNull(viewModel.attachmentState.value.imageLimitError)
        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenSwitchingFromDuckAiModeToOffThenLimitsRelax() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(4)
        addImages(2)
        assertTrue(viewModel.attachmentState.value.isAtCapacity)

        viewModel.setDuckAiMode(false)

        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    private fun addImages(
        count: Int,
        base64: String = "data",
        format: String = "png",
    ) {
        val field = AttachmentViewModel::class.java.getDeclaredField("_imageAttachments")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<List<ImageAttachment>>
        val existing = flow.value
        val bitmap: Bitmap = mock()
        val newImages = (1..count).map {
            ImageAttachment(
                id = java.util.UUID.randomUUID().toString(),
                bitmap = bitmap,
                base64Data = base64,
                format = format,
            )
        }
        flow.value = existing + newImages
    }

    private fun aModel(
        id: String,
        supportsImageUpload: Boolean = false,
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = emptyList(),
        isAccessible = true,
        supportsImageUpload = supportsImageUpload,
    )

    private class FakeLimitsHandler : LimitsHandler {
        private val _imageUploadLimitReached = MutableStateFlow(false)
        override val imageUploadLimitReached: StateFlow<Boolean> = _imageUploadLimitReached

        private val _conversationImagesSent = MutableStateFlow(0)
        override val conversationImagesSent: StateFlow<Int> = _conversationImagesSent

        override fun setImageUploadLimitReached(reached: Boolean) {
            _imageUploadLimitReached.value = reached
        }

        override fun setConversationImagesUsed(count: Int) {
            _conversationImagesSent.value = count
        }

        override fun resetConversationImagesSent() {
            _conversationImagesSent.value = 0
        }

        /** Test helper: increments the conversation-sent counter directly. */
        fun addConversationImagesSent(count: Int) {
            _conversationImagesSent.value += count
        }
    }
}
