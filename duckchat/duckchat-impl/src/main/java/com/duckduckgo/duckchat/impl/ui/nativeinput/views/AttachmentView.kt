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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.Action
import com.duckduckgo.duckchat.impl.ui.AttachmentViewModel
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachmentsContainerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray

@SuppressLint("ViewConstructor")
class AttachmentView(
    context: Context,
) : FrameLayout(context) {

    var onAction: ((Action) -> Unit)? = null
    var onCameraCaptureRequested: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onFilePickerRequested: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)? = null

    private var viewModel: AttachmentViewModel? = null
    private var thumbnailsLayout: LinearLayout? = null
    private var imageAttachmentsContainer: ImageAttachmentsContainerView? = null
    private var fileAttachmentsContainer: FileAttachmentsContainerView? = null
    private var limitErrorView: TextView? = null

    init {
        addView(buildAttachButton())
        setOnClickListener { showChooserDialog() }
    }

    fun bind(scope: CoroutineScope, factory: ViewViewModelFactory) {
        val owner = findViewTreeViewModelStoreOwner() ?: return
        val vm = ViewModelProvider(owner, factory)[AttachmentViewModel::class.java]
        viewModel = vm
        val container = rootView?.findViewById<FrameLayout>(R.id.attachmentsContainer) ?: return
        setupContainerViews(container, vm)
        scope.launch {
            vm.attachmentState.collect { state -> applyState(state, container) }
        }
    }

    fun getImageAttachments(): List<ImageAttachment> = viewModel?.getImageAttachments() ?: emptyList()

    fun getFileAttachments(): List<FileAttachment> = viewModel?.getFileAttachments() ?: emptyList()

    fun getImageAttachmentsJson(): JSONArray? = viewModel?.getImageAttachmentsJson()

    fun getFileAttachmentsJson(): JSONArray? = viewModel?.getFileAttachmentsJson()

    fun clearAttachments() = viewModel?.clearAttachments()

    fun clearAttachmentsForNewChat() = viewModel?.clearAttachmentsForNewChat()

    fun setDuckAiMode(enabled: Boolean) = viewModel?.setDuckAiMode(enabled)

    private fun buildAttachButton(): ImageView {
        val iconSize = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        return ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_attach_16)
        }
    }

    private fun setupContainerViews(container: FrameLayout, vm: AttachmentViewModel) {
        if (thumbnailsLayout != null) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
        container.addView(layout)
        thumbnailsLayout = layout

        addScrollableAttachmentsRow(layout, vm)
        limitErrorView = buildLimitErrorView(layout)
    }

    private fun addScrollableAttachmentsRow(parent: LinearLayout, vm: AttachmentViewModel) {
        val scroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            isHorizontalScrollBarEnabled = false
        }
        parent.addView(scroll)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        scroll.addView(row)

        val imagesContainer = ImageAttachmentsContainerView(context).also {
            it.onAttachmentRemoved = { id -> vm.removeImageAttachment(id) }
        }
        row.addView(imagesContainer)
        imageAttachmentsContainer = imagesContainer

        val filesContainer = FileAttachmentsContainerView(context).also {
            it.onAttachmentRemoved = { attachment -> vm.removeFileAttachment(attachment.id) }
        }
        row.addView(filesContainer)
        fileAttachmentsContainer = filesContainer
    }

    private fun buildLimitErrorView(parent: LinearLayout): TextView {
        return TextView(context).apply {
            setPadding(12.toPx(), 4.toPx(), 12.toPx(), 4.toPx())
            setTextColor(resources.getColor(com.duckduckgo.mobile.android.R.color.red50, null))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            visibility = GONE
        }.also { parent.addView(it) }
    }

    private fun applyState(state: AttachmentViewModel.AttachmentState, container: FrameLayout) {
        val imagesView = imageAttachmentsContainer ?: return
        syncImages(imagesView, state)
        syncFiles(state)
        val errorMessage = state.imageLimitError ?: state.fileLimitError ?: state.fileSizeError ?: state.filePageCountError
        container.isVisible = state.hasAttachments || errorMessage != null
        updateLimitError(errorMessage)
        notifyStateChanged(state)
    }

    private fun syncImages(imagesView: ImageAttachmentsContainerView, state: AttachmentViewModel.AttachmentState) {
        val stateIds = state.images.map { it.id }.toSet()
        val containerIds = imagesView.getAttachmentIds().toSet()
        (containerIds - stateIds).forEach { id -> imagesView.removeAttachmentById(id) }
        (stateIds - containerIds).forEach { id ->
            state.images.find { it.id == id }?.let { imagesView.addAttachment(it) }
        }
    }

    private fun syncFiles(state: AttachmentViewModel.AttachmentState) {
        val filesView = fileAttachmentsContainer ?: return
        val stateFileIds = state.files.map { it.id }.toSet()
        val containerFileIds = filesView.getAttachments().map { it.id }.toSet()
        (containerFileIds - stateFileIds).forEach { id ->
            filesView.getAttachments().find { it.id == id }?.let { filesView.removeAttachment(it) }
        }
        (stateFileIds - containerFileIds).forEach { id ->
            state.files.find { it.id == id }?.let { filesView.addAttachment(it) }
        }
    }

    private fun updateLimitError(errorMessage: String?) {
        limitErrorView?.text = errorMessage
        limitErrorView?.visibility = if (errorMessage != null) VISIBLE else GONE
    }

    private fun notifyStateChanged(state: AttachmentViewModel.AttachmentState) {
        onAction?.invoke(
            Action.AttachmentStateChanged(
                hasAttachments = state.hasAttachments,
                limitExceeded = state.imageLimitError != null && state.hasAttachments,
                supportsUpload = state.supportsUpload,
            ),
        )
    }

    private fun showChooserDialog() {
        val state = viewModel?.attachmentState?.value
        val supportedFileTypes = state?.supportedFileTypes.orEmpty()
        val supportsImages = state?.supportsImageUpload == true
        val mimeTypes = state?.acceptedMimeTypes ?: listOf("image/*")

        onAction?.invoke(Action.ShowAttachmentChooser(true))
        ActionBottomSheetDialog.Builder(context)
            .setTitle(context.getString(R.string.imageCaptureCameraGalleryDisambiguationTitle))
            .setPrimaryItem(
                context.getString(R.string.imageCaptureCameraGalleryDisambiguationGalleryOption),
                com.duckduckgo.mobile.android.R.drawable.ic_image_24,
            )
            .setSecondaryItem(
                context.getString(R.string.imageCaptureCameraGalleryDisambiguationCameraOption),
                com.duckduckgo.mobile.android.R.drawable.ic_camera_24,
            )
            .addEventListener(buildDialogListener(supportsImages, supportedFileTypes, mimeTypes))
            .show()
    }

    private fun buildDialogListener(
        supportsImages: Boolean,
        supportedFileTypes: List<String>,
        mimeTypes: List<String>,
    ) = object : ActionBottomSheetDialog.EventListener() {
        private var pickerLaunched = false

        override fun onPrimaryItemClicked() {
            pickerLaunched = true
            val callback = if (supportsImages && supportedFileTypes.isNotEmpty()) {
                buildCombinedPickerCallback()
            } else if (supportedFileTypes.isNotEmpty()) {
                buildFilePickerCallback()
            } else {
                buildImagePickerCallback()
            }
            onFilePickerRequested?.invoke(callback, mimeTypes)
        }

        override fun onSecondaryItemClicked() {
            pickerLaunched = true
            onCameraCaptureRequested?.invoke(buildImagePickerCallback())
        }

        override fun onBottomSheetDismissed() {
            if (!pickerLaunched) onAction?.invoke(Action.ShowAttachmentChooser(false))
        }
    }

    private fun buildImagePickerCallback(): ValueCallback<Array<Uri>> = ValueCallback { uris ->
        val list = uris?.toList()
        if (!list.isNullOrEmpty()) viewModel?.onImagesPicked(list)
    }

    private fun buildFilePickerCallback(): ValueCallback<Array<Uri>> = ValueCallback { uris ->
        val list = uris?.toList()
        if (!list.isNullOrEmpty()) viewModel?.onFilesPicked(list)
    }

    private fun buildCombinedPickerCallback(): ValueCallback<Array<Uri>> = ValueCallback { uris ->
        val list = uris?.toList() ?: return@ValueCallback
        val imageUris = list.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
        val fileUris = list - imageUris.toSet()
        if (imageUris.isNotEmpty()) viewModel?.onImagesPicked(imageUris)
        if (fileUris.isNotEmpty()) viewModel?.onFilesPicked(fileUris)
    }
}
