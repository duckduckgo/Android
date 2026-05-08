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

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.AttachmentLimits
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.FileLimits
import com.duckduckgo.duckchat.impl.models.ImageLimits
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.LimitsHandler
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachmentProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AttachmentViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChatInternal: DuckChatInternal = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val limitsHandler: FakeLimitsHandler = FakeLimitsHandler()
    private val fileAttachmentProcessor: FakeFileAttachmentProcessor = FakeFileAttachmentProcessor()
    private val modelStateFlow = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(modelStateFlow)
    }
    private val context: Context = mock<Context>().also {
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatImageAttachmentLimitPerConversation, 5))
            .thenReturn("Conversation limit reached")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatImageAttachmentLimitPerMessage, 3))
            .thenReturn("Per-message limit reached")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentLimitPerConversation, 2))
            .thenReturn("File conversation limit reached")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentLimitPerConversation, 3))
            .thenReturn("File conversation limit reached")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentTooLarge, 5))
            .thenReturn("File too large")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentTooManyPages, 5))
            .thenReturn("Too many pages")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentTooManyPages, 10))
            .thenReturn("Too many pages")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentTotalSizeLimitExceeded, 5))
            .thenReturn("Total file size limit exceeded")
        whenever(it.getString(com.duckduckgo.duckchat.impl.R.string.duckChatFileAttachmentTotalSizeLimitExceeded, 10))
            .thenReturn("Total file size limit exceeded")
    }

    private lateinit var viewModel: AttachmentViewModel

    @Before
    fun setUp() {
        viewModel = AttachmentViewModel(
            duckChatInternal = duckChatInternal,
            dispatchers = coroutineRule.testDispatcherProvider,
            modelManager = modelManager,
            limitsHandler = limitsHandler,
            fileAttachmentProcessor = fileAttachmentProcessor,
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
    fun whenModelHasSupportedFileTypesThenSupportsUploadIsTrue() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(false)
        val model = aModel(id = "m1", supportedFileTypes = listOf("application/pdf"))
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        assertTrue(viewModel.attachmentState.value.supportsUpload)
    }

    @Test
    fun whenModelHasSupportedFileTypesThenAcceptedMimeTypesContainsThem() = runTest {
        val model = aModel(id = "m1", supportedFileTypes = listOf("application/pdf", "text/plain"))
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        val mimeTypes = viewModel.attachmentState.value.acceptedMimeTypes
        assertTrue(mimeTypes.contains("application/pdf"))
        assertTrue(mimeTypes.contains("text/plain"))
    }

    @Test
    fun whenModelSupportsImageUploadThenAcceptedMimeTypesContainsImageWildcard() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(true)
        val model = aModel(id = "m1", supportsImageUpload = true)
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        assertTrue(viewModel.attachmentState.value.acceptedMimeTypes.contains("image/*"))
    }

    @Test
    fun whenModelSupportsFilesAndImagesThenAcceptedMimeTypesContainsBoth() = runTest {
        whenever(duckChatInternal.isImageUploadEnabled()).thenReturn(true)
        val model = aModel(id = "m1", supportsImageUpload = true, supportedFileTypes = listOf("application/pdf"))
        modelStateFlow.value = ModelState(models = listOf(model), selectedModelId = "m1")

        val mimeTypes = viewModel.attachmentState.value.acceptedMimeTypes
        assertTrue(mimeTypes.contains("application/pdf"))
        assertTrue(mimeTypes.contains("image/*"))
    }

    @Test
    fun whenNoAttachmentsAndNoLimitsReachedThenIsAtCapacityIsFalse() = runTest {
        assertFalse(viewModel.attachmentState.value.isAtCapacity)
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
    fun whenTotalImagesAtConversationLimitThenIsAtCapacityIsTrue() = runTest {
        val limits = ImageLimits(maxPerTurn = 5, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(3)
        addImages(2)

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenUnderAllLimitsThenImageLimitErrorIsNull() = runTest {
        assertNull(viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenTotalImagesOverConversationLimitThenConversationErrorShown() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(3)
        addImages(3)

        assertEquals("Conversation limit reached", viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenCurrentImagesOverPerTurnLimitThenPerTurnErrorShown() = runTest {
        val limits = ImageLimits(maxPerTurn = 3, maxPerConversation = 20)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        addImages(4)

        assertEquals("Per-message limit reached", viewModel.attachmentState.value.imageLimitError)
    }

    @Test
    fun whenConversationImagesSentCountedInTotal() = runTest {
        val limits = ImageLimits(maxPerTurn = 10, maxPerConversation = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(images = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationImagesSent(4)
        addImages(2)

        assertNotNull(viewModel.attachmentState.value.imageLimitError)
        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenFilesAtConversationLimitThenIsAtCapacityIsTrue() = runTest {
        val limits = FileLimits(maxPerConversation = 2)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        advanceUntilIdle()
        addFiles(aFileAttachment(), aFileAttachment())
        advanceUntilIdle()

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenFilesBelowConversationLimitThenIsAtCapacityIsFalse() = runTest {
        val limits = FileLimits(maxPerConversation = 3)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        advanceUntilIdle()
        addFiles(aFileAttachment())
        advanceUntilIdle()

        assertFalse(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenConversationFilesSentPushesTotalToLimitThenIsAtCapacityIsTrue() = runTest {
        val limits = FileLimits(maxPerConversation = 3)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFilesSent(2)
        addFiles(aFileAttachment())
        advanceUntilIdle()

        assertTrue(viewModel.attachmentState.value.isAtCapacity)
    }

    @Test
    fun whenNoAttachmentsThenHasAttachmentsIsFalse() = runTest {
        assertFalse(viewModel.attachmentState.value.hasAttachments)
    }

    @Test
    fun whenImagesAddedThenHasAttachmentsIsTrue() = runTest {
        addImages(1)

        assertTrue(viewModel.attachmentState.value.hasAttachments)
    }

    @Test
    fun whenFilesAddedThenHasAttachmentsIsTrue() = runTest {
        addFiles(aFileAttachment())

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
    fun whenRemovingNonExistentImageIdThenListUnchanged() = runTest {
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
    fun whenFileRemovedByIdThenItIsNoLongerInState() = runTest {
        val file = aFileAttachment(id = "file-1")
        addFiles(file)

        viewModel.removeFileAttachment("file-1")

        assertTrue(viewModel.attachmentState.value.files.isEmpty())
    }

    @Test
    fun whenRemovingNonExistentFileIdThenListUnchanged() = runTest {
        addFiles(aFileAttachment(), aFileAttachment())

        viewModel.removeFileAttachment("nonexistent-id")

        assertEquals(2, viewModel.attachmentState.value.files.size)
    }

    @Test
    fun whenClearAttachmentsCalledThenFilesAreEmpty() = runTest {
        addFiles(aFileAttachment())

        viewModel.clearAttachments()

        assertTrue(viewModel.attachmentState.value.files.isEmpty())
    }

    @Test
    fun whenClearAttachmentsForNewChatCalledThenFilesAreEmpty() = runTest {
        addFiles(aFileAttachment())

        viewModel.clearAttachmentsForNewChat()

        assertTrue(viewModel.attachmentState.value.files.isEmpty())
    }

    @Test
    fun whenProcessFileReturnsNullThenFileNotAdded() = runTest {
        val uri: Uri = mock()

        viewModel.onFilesPicked(listOf(uri))
        advanceUntilIdle()

        assertTrue(viewModel.attachmentState.value.files.isEmpty())
    }

    @Test
    fun whenUnderFileLimitThenFileLimitErrorIsNull() = runTest {
        val limits = FileLimits(maxPerConversation = 3)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(), aFileAttachment())

        assertNull(viewModel.attachmentState.value.fileLimitError)
    }

    @Test
    fun whenTotalFilesOverConversationLimitThenFileLimitErrorShown() = runTest {
        val limits = FileLimits(maxPerConversation = 2)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFilesSent(2)
        addFiles(aFileAttachment())

        assertEquals("File conversation limit reached", viewModel.attachmentState.value.fileLimitError)
    }

    @Test
    fun whenConversationFilesSentCountedInTotal() = runTest {
        val limits = FileLimits(maxPerConversation = 3)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFilesSent(2)
        addFiles(aFileAttachment(), aFileAttachment())

        assertNotNull(viewModel.attachmentState.value.fileLimitError)
    }

    @Test
    fun whenFileSizeWithinLimitThenFileSizeErrorIsNull() = runTest {
        val maxBytes = 5L * 1024 * 1024
        val limits = FileLimits(maxFileSizeBytes = maxBytes)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(sizeBytes = maxBytes - 1))

        assertNull(viewModel.attachmentState.value.fileSizeError)
    }

    @Test
    fun whenFileSizeExceedsLimitThenFileSizeErrorShown() = runTest {
        val maxBytes = 5L * 1024 * 1024
        val limits = FileLimits(maxFileSizeBytes = maxBytes)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(sizeBytes = maxBytes + 1))

        assertEquals("File too large", viewModel.attachmentState.value.fileSizeError)
    }

    @Test
    fun whenOversizedFileRemovedThenFileSizeErrorClears() = runTest {
        val maxBytes = 5L * 1024 * 1024
        val limits = FileLimits(maxFileSizeBytes = maxBytes)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        val file = aFileAttachment(id = "big-file", sizeBytes = maxBytes + 1)
        addFiles(file)
        assertNotNull(viewModel.attachmentState.value.fileSizeError)

        viewModel.removeFileAttachment("big-file")

        assertNull(viewModel.attachmentState.value.fileSizeError)
    }

    @Test
    fun whenFilePageCountWithinLimitThenPageCountErrorIsNull() = runTest {
        val limits = FileLimits(maxPagesPerFile = 10)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(pageCount = 10))

        assertNull(viewModel.attachmentState.value.filePageCountError)
    }

    @Test
    fun whenFilePageCountExceedsLimitThenPageCountErrorShown() = runTest {
        val limits = FileLimits(maxPagesPerFile = 10)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(pageCount = 11))

        assertEquals("Too many pages", viewModel.attachmentState.value.filePageCountError)
    }

    @Test
    fun whenNonPdfFileHasNoPageCountThenPageCountErrorIsNull() = runTest {
        val limits = FileLimits(maxPagesPerFile = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(mimeType = "text/plain", pageCount = null))

        assertNull(viewModel.attachmentState.value.filePageCountError)
    }

    @Test
    fun whenTooManyPagesFileRemovedThenPageCountErrorClears() = runTest {
        val limits = FileLimits(maxPagesPerFile = 5)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        val file = aFileAttachment(id = "big-pdf", pageCount = 100)
        addFiles(file)
        assertNotNull(viewModel.attachmentState.value.filePageCountError)

        viewModel.removeFileAttachment("big-pdf")

        assertNull(viewModel.attachmentState.value.filePageCountError)
    }

    @Test
    fun whenTotalFileSizeWithinLimitThenFileTotalSizeErrorIsNull() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(sizeBytes = maxTotal - 1))

        assertNull(viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenTotalFileSizeExactlyAtLimitThenFileTotalSizeErrorIsNull() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(sizeBytes = maxTotal))

        assertNull(viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenCurrentSessionFilesExceedTotalLimitThenFileTotalSizeErrorShown() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(aFileAttachment(sizeBytes = maxTotal + 1))

        assertEquals("Total file size limit exceeded", viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenMultipleFilesTogetherExceedTotalLimitThenFileTotalSizeErrorShown() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        addFiles(
            aFileAttachment(sizeBytes = 3L * 1024 * 1024),
            aFileAttachment(sizeBytes = 3L * 1024 * 1024),
        )

        assertEquals("Total file size limit exceeded", viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenConversationFileSizeSentPushesTotalOverLimitInDuckAiModeThenFileTotalSizeErrorShown() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFileSizeSent(4L * 1024 * 1024)
        addFiles(aFileAttachment(sizeBytes = 2L * 1024 * 1024))

        assertEquals("Total file size limit exceeded", viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenConversationFileSizeSentAloneExceedsLimitInDuckAiModeThenFileTotalSizeErrorShown() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFileSizeSent(maxTotal + 1)

        assertEquals("Total file size limit exceeded", viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenNotInDuckAiModeConversationFileSizeSentIsNotCountedTowardsTotal() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        limitsHandler.addConversationFileSizeSent(maxTotal + 1)
        addFiles(aFileAttachment(sizeBytes = 1024L))

        assertNull(viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenFileRemovedSoTotalDropsBelowLimitThenFileTotalSizeErrorClears() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        val bigFile = aFileAttachment(id = "big-file", sizeBytes = 3L * 1024 * 1024)
        addFiles(bigFile, aFileAttachment(sizeBytes = 3L * 1024 * 1024))
        assertNotNull(viewModel.attachmentState.value.fileTotalSizeError)

        viewModel.removeFileAttachment("big-file")

        assertNull(viewModel.attachmentState.value.fileTotalSizeError)
    }

    @Test
    fun whenClearAttachmentsForNewChatCalledThenConversationFileSizeSentIsReset() = runTest {
        val maxTotal = 5L * 1024 * 1024
        val limits = FileLimits(maxTotalFileSizeBytes = maxTotal)
        modelStateFlow.value = ModelState(attachmentLimits = AttachmentLimits(files = limits))
        viewModel.setDuckAiMode(true)
        limitsHandler.addConversationFileSizeSent(maxTotal + 1)

        viewModel.clearAttachmentsForNewChat()

        assertNull(viewModel.attachmentState.value.fileTotalSizeError)
        assertEquals(0L, limitsHandler.conversationFileSizeSentBytes.value)
    }

    @Test
    fun whenNoFilesThenGetFileAttachmentsReturnsEmptyList() = runTest {
        assertTrue(viewModel.getFileAttachments().isEmpty())
    }

    @Test
    fun whenFilesAddedThenGetFileAttachmentsReturnsThem() = runTest {
        addFiles(aFileAttachment(), aFileAttachment())

        assertEquals(2, viewModel.getFileAttachments().size)
    }

    @Test
    fun whenNoFilesThenGetFileAttachmentsJsonReturnsNull() = runTest {
        assertNull(viewModel.getFileAttachmentsJson())
    }

    @Test
    fun whenFilesAddedThenGetFileAttachmentsJsonContainsCorrectFields() = runTest {
        addFiles(aFileAttachment(fileName = "doc.pdf", mimeType = "application/pdf", base64Data = "base64abc"))

        val json = viewModel.getFileAttachmentsJson()

        assertNotNull(json)
        assertEquals(1, json!!.length())
        val item = json.getJSONObject(0)
        assertEquals("base64abc", item.getString("data"))
        assertEquals("doc.pdf", item.getString("fileName"))
        assertEquals("application/pdf", item.getString("mimeType"))
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
    fun whenContentResolverReturnsNullStreamThenImageAttachmentListRemainsUnchanged() = runTest {
        val contentResolver: android.content.ContentResolver = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(contentResolver.openInputStream(any())).thenReturn(null)
        val uri: Uri = mock()

        viewModel.onImagesPicked(listOf(uri))
        advanceUntilIdle()

        assertTrue(viewModel.attachmentState.value.images.isEmpty())
    }

    private fun addImages(
        count: Int,
        base64: String = "data",
        format: String = "png",
    ) {
        val bitmap: Bitmap = mock()
        val newImages = (1..count).map {
            ImageAttachment(
                id = UUID.randomUUID().toString(),
                bitmap = bitmap,
                base64Data = base64,
                format = format,
            )
        }
        viewModel.imageAttachments.value += newImages
    }

    private fun addFiles(vararg attachments: FileAttachment) {
        for (attachment in attachments) {
            val uri: Uri = mock()
            fileAttachmentProcessor.givenProcessFileReturns(uri, attachment)
            viewModel.onFilesPicked(listOf(uri))
        }
    }

    private fun aFileAttachment(
        id: String = UUID.randomUUID().toString(),
        fileName: String = "test.pdf",
        mimeType: String = "application/pdf",
        sizeBytes: Long = 1024L,
        base64Data: String = "filedata",
        pageCount: Int? = null,
    ): FileAttachment = FileAttachment(
        id = id,
        uri = mock(),
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        base64Data = base64Data,
        pageCount = pageCount,
    )

    private fun aModel(
        id: String,
        supportsImageUpload: Boolean = false,
        supportedFileTypes: List<String> = emptyList(),
    ) = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = emptyList(),
        isAccessible = true,
        supportsImageUpload = supportsImageUpload,
        supportedFileTypes = supportedFileTypes,
    )

    private class FakeLimitsHandler : LimitsHandler {
        private val _conversationImagesSent = MutableStateFlow(0)
        override val conversationImagesSent: StateFlow<Int> = _conversationImagesSent

        private val _conversationFilesSent = MutableStateFlow(0)
        override val conversationFilesSent: StateFlow<Int> = _conversationFilesSent

        private val _conversationFileSizeSentBytes = MutableStateFlow(0L)
        override val conversationFileSizeSentBytes: StateFlow<Long> = _conversationFileSizeSentBytes

        override fun setConversationImagesUsed(count: Int) {
            _conversationImagesSent.value = count
        }

        override fun setConversationFilesUsed(count: Int, sizeBytes: Long) {
            _conversationFilesSent.value = count
            _conversationFileSizeSentBytes.value = sizeBytes
        }

        fun addConversationImagesSent(count: Int) {
            _conversationImagesSent.value += count
        }

        fun addConversationFilesSent(count: Int) {
            _conversationFilesSent.value += count
        }

        fun addConversationFileSizeSent(sizeBytes: Long) {
            _conversationFileSizeSentBytes.value += sizeBytes
        }
    }

    private class FakeFileAttachmentProcessor : FileAttachmentProcessor {
        private val responses = mutableMapOf<Uri, FileAttachment?>()

        fun givenProcessFileReturns(uri: Uri, attachment: FileAttachment?) {
            responses[uri] = attachment
        }

        override suspend fun processFile(context: Context, uri: Uri): FileAttachment? = responses[uri]
    }
}
