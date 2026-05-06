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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.ValueCallback
import androidx.core.graphics.scale
import com.squareup.anvil.annotations.ContributesBinding
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AttachmentHandler {
    var onImageAttachmentAdded: ((ImageAttachment) -> Unit)?
    var onImageLimitError: ((String) -> Unit)?
    var onImageLimitErrorClear: (() -> Unit)?
    var onImagePickerRequested: ((ValueCallback<Array<Uri>>) -> Unit)?
    var onFilePickerRequested: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)?
    var onChooserStateChanged: ((Boolean) -> Unit)?
    var onRequestFocus: (() -> Unit)?
    var conversationImageLimitReached: Boolean
    val imageUploadLimitReached: Flow<Boolean>

    fun supportsImageUpload(): Boolean
    fun handlePickedImages(uris: List<Uri>)
    fun onImagesSubmitted(count: Int)
    fun resetConversationCounts()
    fun updateImageCount(count: Int)
    fun isAtMaxCapacity(): Boolean
    fun showAttachmentChooser()
}

class RealAttachmentHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val duckChatInternal: DuckChatInternal,
    private val dispatcherProvider: DispatcherProvider,
    private val modelManager: DuckAiModelManager,
    private val limitsHandler: LimitsHandler,
    private val appBuildConfig: AppBuildConfig,
) : AttachmentHandler {

    override var onImageAttachmentAdded: ((ImageAttachment) -> Unit)? = null
    override var onImageLimitError: ((String) -> Unit)? = null
    override var onImageLimitErrorClear: (() -> Unit)? = null
    override var onImagePickerRequested: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    override var onFilePickerRequested: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)? = null
    override var onChooserStateChanged: ((Boolean) -> Unit)? = null
    override var onRequestFocus: (() -> Unit)? = null

    private var imageCount: Int = 0
    override var conversationImageLimitReached: Boolean = false

    private val contentResolver: ContentResolver get() = context.contentResolver

    override val imageUploadLimitReached: Flow<Boolean>
        get() = limitsHandler.imageUploadLimitReached

    override fun supportsImageUpload(): Boolean {
        val state = modelManager.modelState.value
        if (state.models.isEmpty()) return true
        val model = state.models.find { it.id == state.selectedModelId }
        return model?.supportsImageUpload == true && duckChatInternal.isImageUploadEnabled()
    }

    override fun handlePickedImages(uris: List<Uri>) {
        scope.launch(dispatcherProvider.main()) {
            uris.forEach { uri ->
                withContext(dispatcherProvider.io()) { processImage(uri) }?.let { attachment ->
                    onImageAttachmentAdded?.invoke(attachment)
                }
            }
            onRequestFocus?.invoke()
        }
    }

    override fun onImagesSubmitted(count: Int) {
        limitsHandler.addConversationImagesSent(count)
    }

    override fun resetConversationCounts() {
        limitsHandler.resetConversationImagesSent()
    }

    override fun updateImageCount(count: Int) {
        imageCount = count
        val limits = modelManager.modelState.value.attachmentLimits.images
        val totalImages = imageCount + limitsHandler.conversationImagesSent.value
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

    override fun isAtMaxCapacity(): Boolean {
        if (!supportsImageUpload()) return true
        val limits = modelManager.modelState.value.attachmentLimits.images
        val totalImages = imageCount + limitsHandler.conversationImagesSent.value
        return conversationImageLimitReached || imageCount >= limits.maxPerTurn || totalImages >= limits.maxPerConversation
    }

    override fun showAttachmentChooser() {
        if (!supportsImageUpload()) return

        onChooserStateChanged?.invoke(true)

        val chooseFileString = context.getString(R.string.imageCaptureCameraGalleryDisambiguationGalleryOption)
        val chooseFileIcon = com.duckduckgo.mobile.android.R.drawable.ic_image_24
        val takePhotoString = context.getString(R.string.imageCaptureCameraGalleryDisambiguationCameraOption)
        val takePhotoIcon = com.duckduckgo.mobile.android.R.drawable.ic_camera_24

        com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog.Builder(context)
            .setTitle(context.getString(R.string.imageCaptureCameraGalleryDisambiguationTitle))
            .setPrimaryItem(chooseFileString, chooseFileIcon)
            .setSecondaryItem(takePhotoString, takePhotoIcon)
            .addEventListener(object : com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog.EventListener() {
                private var pickerLaunched = false

                override fun onPrimaryItemClicked() {
                    pickerLaunched = true
                    val callback = ValueCallback<Array<Uri>> { uris ->
                        uris?.toList()?.let { handlePickedImages(it) }
                            ?: onRequestFocus?.invoke()
                    }
                    onFilePickerRequested?.invoke(callback, listOf("image/*"))
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
    private fun getCompressFormat(format: String): Bitmap.CompressFormat {
        val compressFormat = when (format) {
            "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> if (appBuildConfig.sdkInt >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        return compressFormat
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

interface AttachmentHandlerFactory {
    fun create(context: Context, scope: CoroutineScope): AttachmentHandler
}

@ContributesBinding(AppScope::class)
class RealAttachmentHandlerFactory @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    private val dispatcherProvider: DispatcherProvider,
    private val modelManager: DuckAiModelManager,
    private val limitsHandler: LimitsHandler,
    private val appBuildConfig: AppBuildConfig,
) : AttachmentHandlerFactory {

    override fun create(context: Context, scope: CoroutineScope): AttachmentHandler = RealAttachmentHandler(
        context = context,
        scope = scope,
        duckChatInternal = duckChatInternal,
        dispatcherProvider = dispatcherProvider,
        modelManager = modelManager,
        limitsHandler = limitsHandler,
        appBuildConfig = appBuildConfig,
    )
}
