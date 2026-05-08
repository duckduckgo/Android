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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ImageLimits
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.LimitsHandler
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachmentProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

@SuppressLint("StaticFieldLeak")
@ContributesViewModel(ViewScope::class)
class AttachmentViewModel @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    private val dispatchers: DispatcherProvider,
    modelManager: DuckAiModelManager,
    private val limitsHandler: LimitsHandler,
    private val fileAttachmentProcessor: FileAttachmentProcessor,
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : ViewModel() {

    data class AttachmentState(
        val images: List<ImageAttachment> = emptyList(),
        val files: List<FileAttachment> = emptyList(),
        val imageLimitError: String? = null,
        val fileLimitError: String? = null,
        val fileSizeError: String? = null,
        val filePageCountError: String? = null,
        val supportsUpload: Boolean = false,
        val supportsImageUpload: Boolean = false,
        val supportedFileTypes: List<String> = emptyList(),
        val isAtCapacity: Boolean = false,
    ) {
        val hasAttachments: Boolean get() = images.isNotEmpty() || files.isNotEmpty()
        val acceptedMimeTypes: List<String> get() {
            val types = mutableListOf<String>()
            if (supportedFileTypes.isNotEmpty()) types.addAll(supportedFileTypes)
            if (supportsImageUpload) types.add("image/*")
            return types.ifEmpty { listOf("image/*") }
        }
    }

    @VisibleForTesting
    internal val imageAttachments = MutableStateFlow<List<ImageAttachment>>(emptyList())
    private val _fileAttachments = MutableStateFlow<List<FileAttachment>>(emptyList())
    private val _isDuckAiMode = MutableStateFlow(false)

    val attachmentState: StateFlow<AttachmentState> = combine(
        combine(imageAttachments, _fileAttachments) { images, files -> Pair(images, files) },
        modelManager.modelState,
        combine(limitsHandler.conversationImagesSent, limitsHandler.conversationFilesSent) { imgSent, fileSent -> Pair(imgSent, fileSent) },
        _isDuckAiMode,
    ) { (images, files), modelState, (conversationImagesSent, conversationFilesSent), isDuckAiMode ->
        val model = modelState.models.find { it.id == modelState.selectedModelId }
        val supportsImageUpload = modelState.models.isEmpty() ||
            (model?.supportsImageUpload == true && duckChatInternal.isImageUploadEnabled())
        val supportedFileTypes = model?.supportedFileTypes.orEmpty()
        val imageLimits = modelState.attachmentLimits.images
        val fileLimits = modelState.attachmentLimits.files
        val currentImageCount = images.size
        val totalImages = currentImageCount + if (isDuckAiMode) conversationImagesSent else 0
        val totalFiles = files.size + if (isDuckAiMode) conversationFilesSent else 0
        AttachmentState(
            images = images,
            files = files,
            imageLimitError = computeImageLimitError(currentImageCount, totalImages, imageLimits),
            fileLimitError = computeFileLimitError(totalFiles, fileLimits.maxPerConversation),
            fileSizeError = computeFileSizeError(files, fileLimits.maxFileSizeBytes),
            filePageCountError = computeFilePageCountError(files, fileLimits.maxPagesPerFile),
            supportsUpload = supportsImageUpload || supportedFileTypes.isNotEmpty(),
            supportsImageUpload = supportsImageUpload,
            supportedFileTypes = supportedFileTypes,
            isAtCapacity = currentImageCount >= imageLimits.maxPerTurn || totalImages >= imageLimits.maxPerConversation,
        )
    }.stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = AttachmentState())

    fun onImagesPicked(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val attachment = withContext(dispatchers.io()) { processImage(uri) } ?: return@forEach
                imageAttachments.update { it + attachment }
            }
        }
    }

    fun onFilesPicked(uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val attachment = fileAttachmentProcessor.processFile(context, uri) ?: continue
                _fileAttachments.update { it + attachment }
            }
        }
    }

    fun removeImageAttachment(id: String) {
        var toRecycle: Bitmap? = null
        imageAttachments.update { list ->
            toRecycle = list.find { it.id == id }?.bitmap
            list.filter { it.id != id }
        }
        viewModelScope.launch { toRecycle?.recycle() }
    }

    fun removeFileAttachment(id: String) {
        _fileAttachments.update { list -> list.filter { it.id != id } }
    }

    fun clearAttachments() {
        val toRecycle = imageAttachments.value
        imageAttachments.value = emptyList()
        _fileAttachments.value = emptyList()
        viewModelScope.launch { toRecycle.forEach { it.bitmap.recycle() } }
    }

    fun clearAttachmentsForNewChat() {
        clearAttachments()
        limitsHandler.setConversationImagesUsed(0)
        limitsHandler.setConversationFilesUsed(0, 0L)
    }

    fun setDuckAiMode(enabled: Boolean) {
        _isDuckAiMode.value = enabled
    }

    fun getImageAttachments(): List<ImageAttachment> = imageAttachments.value

    fun getFileAttachments(): List<FileAttachment> = _fileAttachments.value

    fun getImageAttachmentsJson(): JSONArray? {
        val images = imageAttachments.value
        if (images.isEmpty()) return null
        return JSONArray().apply {
            images.forEach { attachment ->
                put(
                    JSONObject().apply {
                        put("data", attachment.base64Data)
                        put("format", attachment.format)
                    },
                )
            }
        }
    }

    fun getFileAttachmentsJson(): JSONArray? {
        val files = _fileAttachments.value
        if (files.isEmpty()) return null
        return JSONArray().apply {
            files.forEach { attachment ->
                put(
                    JSONObject().apply {
                        put("data", attachment.base64Data)
                        put("fileName", attachment.fileName)
                        put("mimeType", attachment.mimeType)
                    },
                )
            }
        }
    }

    private fun computeImageLimitError(
        currentCount: Int,
        totalImages: Int,
        limits: ImageLimits,
    ): String? = when {
        currentCount > limits.maxPerTurn ->
            context.getString(R.string.duckChatImageAttachmentLimitPerMessage, limits.maxPerTurn)
        totalImages > limits.maxPerConversation ->
            context.getString(R.string.duckChatImageAttachmentLimitPerConversation, limits.maxPerConversation)
        else -> null
    }

    private fun computeFileLimitError(totalFiles: Int, maxPerConversation: Int): String? =
        if (totalFiles > maxPerConversation) {
            context.getString(R.string.duckChatFileAttachmentLimitPerConversation, maxPerConversation)
        } else {
            null
        }

    private fun computeFileSizeError(files: List<FileAttachment>, maxFileSizeBytes: Long): String? {
        if (files.none { it.sizeBytes > maxFileSizeBytes }) return null
        val maxFileSizeMb = (maxFileSizeBytes / (1024 * 1024)).toInt()
        return context.getString(R.string.duckChatFileAttachmentTooLarge, maxFileSizeMb)
    }

    private fun computeFilePageCountError(files: List<FileAttachment>, maxPagesPerFile: Int): String? {
        if (files.none { (it.pageCount ?: 0) > maxPagesPerFile }) return null
        return context.getString(R.string.duckChatFileAttachmentTooManyPages, maxPagesPerFile)
    }

    private fun processImage(uri: Uri): ImageAttachment? {
        val original = decodeBitmap(uri) ?: return null
        val resized = resizeIfNeeded(original, MAX_DIMENSION_PX)
        val format = resolveFormat(uri)
        val compressFormat = getCompressFormat(format)
        val base64 = encodeBitmapToBase64(resized, compressFormat)
        if (resized !== original) original.recycle()
        return ImageAttachment(
            id = UUID.randomUUID().toString(),
            bitmap = resized,
            base64Data = base64,
            format = format,
        )
    }

    @SuppressLint("NewApi")
    private fun getCompressFormat(format: String): Bitmap.CompressFormat = when (format) {
        "jpeg" -> Bitmap.CompressFormat.JPEG
        "webp" -> if (appBuildConfig.sdkInt >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        else -> Bitmap.CompressFormat.PNG
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return bitmap.scale(scaledWidth, scaledHeight)
    }

    private fun resolveFormat(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: return "png"
        return when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpeg"
            mimeType.contains("webp") -> "webp"
            else -> "png"
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap, format: Bitmap.CompressFormat): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, COMPRESSION_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val COMPRESSION_QUALITY = 85
        private const val MAX_DIMENSION_PX = 512
    }
}
