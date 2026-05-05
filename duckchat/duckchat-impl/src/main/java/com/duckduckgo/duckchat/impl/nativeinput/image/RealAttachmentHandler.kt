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

package com.duckduckgo.duckchat.impl.nativeinput.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.ValueCallback
import androidx.core.graphics.scale
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RealAttachmentHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val duckChatInternal: DuckChatInternal,
    private val dispatcherProvider: DispatcherProvider,
    private val modelManager: DuckAiModelManager,
    private val duckChatJSHelper: DuckChatJSHelper,
) {

    var onImageAttachmentAdded: ((ImageAttachment) -> Unit)? = null
    var onImageLimitError: ((String) -> Unit)? = null
    var onImageLimitErrorClear: (() -> Unit)? = null
    var onImagePickerRequested: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onChooserStateChanged: ((Boolean) -> Unit)? = null
    var onRequestFocus: (() -> Unit)? = null

    private var imageCount: Int = 0
    private var conversationImagesSent: Int = 0
    var conversationImageLimitReached: Boolean = false

    private val contentResolver: ContentResolver get() = context.contentResolver

    val imageUploadLimitReached: Flow<Boolean>
        get() = duckChatJSHelper.imageUploadLimitReached

    fun supportsImageUpload(): Boolean {
        val state = modelManager.modelState.value
        val model = state.models.find { it.id == state.selectedModelId }
        return model?.supportsImageUpload == true && duckChatInternal.isImageUploadEnabled()
    }

    fun handlePickedImages(uris: List<Uri>) {
        scope.launch(dispatcherProvider.main()) {
            uris.forEach { uri ->
                withContext(dispatcherProvider.io()) { processImage(uri) }?.let { attachment ->
                    onImageAttachmentAdded?.invoke(attachment)
                }
            }
            onRequestFocus?.invoke()
        }
    }

    fun onImagesSubmitted(count: Int) {
        conversationImagesSent += count
    }

    fun resetConversationCounts() {
        conversationImagesSent = 0
    }

    fun updateImageCount(count: Int) {
        imageCount = count
        val limits = modelManager.modelState.value.attachmentLimits.images
        val totalImages = imageCount + conversationImagesSent
        if (totalImages > limits.maxPerConversation) {
            onImageLimitError?.invoke(
                context.getString(R.string.duckChatImageAttachmentLimitPerConversation, limits.maxPerConversation),
            )
        } else if (imageCount > limits.maxPerTurn) {
            onImageLimitError?.invoke(
                context.getString(R.string.duckChatImageAttachmentLimitPerMessage, limits.maxPerTurn),
            )
        } else {
            onImageLimitErrorClear?.invoke()
        }
    }

    fun isAtMaxCapacity(): Boolean {
        if (!supportsImageUpload()) return true
        val limits = modelManager.modelState.value.attachmentLimits.images
        val totalImages = imageCount + conversationImagesSent
        return conversationImageLimitReached || imageCount >= limits.maxPerTurn || totalImages >= limits.maxPerConversation
    }

    fun showAttachmentChooser() {
        if (!supportsImageUpload() || isAtMaxCapacity()) return

        onChooserStateChanged?.invoke(true)
        var pickerLaunched = false

        val chooseFileString = context.getString(R.string.imageCaptureCameraGalleryDisambiguationGalleryOption)
        val chooseFileIcon = com.duckduckgo.mobile.android.R.drawable.ic_image_24
        val takePhotoString = context.getString(R.string.imageCaptureCameraGalleryDisambiguationCameraOption)
        val takePhotoIcon = com.duckduckgo.mobile.android.R.drawable.ic_camera_24

        com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog.Builder(context)
            .setTitle(context.getString(R.string.imageCaptureCameraGalleryDisambiguationTitle))
            .setPrimaryItem(chooseFileString, chooseFileIcon)
            .setSecondaryItem(takePhotoString, takePhotoIcon)
            .addEventListener(object : com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog.EventListener() {
                override fun onPrimaryItemClicked() {
                    pickerLaunched = true
                    val callback = ValueCallback<Array<Uri>> { uris ->
                        uris?.toList()?.let { handlePickedImages(it) }
                            ?: onRequestFocus?.invoke()
                    }
                    onImagePickerRequested?.invoke(callback)
                }

                override fun onSecondaryItemClicked() {
                    pickerLaunched = true
                    val callback = ValueCallback<Array<Uri>> { uris ->
                        uris?.toList()?.let { handlePickedImages(it) }
                            ?: onRequestFocus?.invoke()
                    }
                    onImagePickerRequested?.invoke(callback)
                }

                override fun onBottomSheetDismissed() {
                    if (!pickerLaunched) {
                        onChooserStateChanged?.invoke(false)
                    }
                }
            }).show()
    }

    private suspend fun processImage(uri: Uri): ImageAttachment? {
        val original = decodeBitmap(uri) ?: return null
        val resized = resizeIfNeeded(original, MAX_DIMENSION_PX)
        val format = resolveFormat(uri)

        @Suppress("DEPRECATION")
        val compressFormat = when (format) {
            "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        val base64 = encodeBitmapToBase64(resized, compressFormat)
        if (resized !== original) original.recycle()
        return ImageAttachment(
            id = UUID.randomUUID().toString(),
            bitmap = resized,
            base64Data = base64,
            format = format,
        )
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return bitmap.scale(newWidth, newHeight)
    }

    private fun resolveFormat(uri: Uri): String {
        val mimeType = contentResolver.getType(uri) ?: return "png"
        return when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpeg"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("gif") -> "gif"
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
