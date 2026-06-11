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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.duckchat.impl.ui.AttachmentViewModel
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachment
import com.duckduckgo.duckchat.impl.ui.nativeinput.file.FileAttachmentsContainerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray

@SuppressLint("ViewConstructor")
class AttachmentView(
    context: Context,
) : FrameLayout(context) {

    var host: NativeInputHost? = null
    var onCameraCaptureRequested: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onFilePickerRequested: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)? = null

    private var viewModel: AttachmentViewModel? = null
    private var supportsUpload: Boolean = false
    private var nativeInputStateJob: Job? = null
    private var lastNativeInputState: NativeInputState? = null
    private var popupWindow: PopupWindow? = null
    private var thumbnailsLayout: LinearLayout? = null
    private var imageAttachmentsContainer: ImageAttachmentsContainerView? = null
    private var fileAttachmentsContainer: FileAttachmentsContainerView? = null
    private var limitErrorView: TextView? = null

    init {
        addView(buildAttachButton())
        setOnClickListener { showPopupMenu() }
    }

    fun bind(scope: CoroutineScope, factory: ViewViewModelFactory, nativeInputStateProvider: NativeInputStateProvider) {
        val owner = findViewTreeViewModelStoreOwner() ?: return
        val vm = ViewModelProvider(owner, factory)[AttachmentViewModel::class.java]
        viewModel = vm
        val container = rootView?.findViewById<FrameLayout>(R.id.attachmentsContainer) ?: return
        setupContainerViews(container, vm)
        scope.launch {
            vm.attachmentState.collect { state -> applyState(state, container) }
        }
        nativeInputStateJob = nativeInputStateProvider.state
            .onEach { state ->
                lastNativeInputState = state
                updateButtonVisibility()
            }
            .launchIn(scope)
    }

    private fun updateButtonVisibility() {
        val show = supportsUpload && lastNativeInputState?.shouldShowPluginControls() == true
        isVisible = show
        (parent as? View)?.isVisible = show
    }

    fun getImageAttachments(): List<ImageAttachment> = viewModel?.getImageAttachments() ?: emptyList()

    fun getFileAttachments(): List<FileAttachment> = viewModel?.getFileAttachments() ?: emptyList()

    fun getImageAttachmentsJson(): JSONArray? = viewModel?.getImageAttachmentsJson()

    fun getFileAttachmentsJson(): JSONArray? = viewModel?.getFileAttachmentsJson()

    fun clearAttachments() = viewModel?.clearAttachments()

    fun clearAttachmentsForNewChat() = viewModel?.clearAttachmentsForNewChat()

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
        val errorMessage =
            state.imageLimitError
                ?: state.fileLimitError
                ?: state.fileSizeError
                ?: state.filePageCountError
                ?: state.fileTotalSizeError
        val notStreaming = lastNativeInputState?.isChatStreaming != true
        container.isVisible = (state.hasAttachments || errorMessage != null) && notStreaming
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
        supportsUpload = state.supportsUpload
        updateButtonVisibility()
        host?.attachmentChanged(
            hasAttachments = state.hasAttachments,
            limitExceeded = (
                state.imageLimitError != null ||
                    state.fileLimitError != null ||
                    state.fileSizeError != null ||
                    state.filePageCountError != null ||
                    state.fileTotalSizeError != null
                ) && state.hasAttachments,
            supportsUpload = state.supportsUpload,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        nativeInputStateJob?.cancel()
        nativeInputStateJob = null
        lastNativeInputState = null
        dismissPopup()
    }

    private fun showPopupMenu() {
        val state = viewModel?.attachmentState?.value
        val supportedFileTypes = state?.supportedFileTypes.orEmpty()
        val supportsImages = state?.supportsImageUpload == true

        if (!supportsImages && supportedFileTypes.isEmpty()) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.popup_menu_bg)
        }
        val popup = PopupWindow(
            ScrollView(context).apply {
                addView(container)
                isVerticalScrollBarEnabled = false
            },
            resources.getDimensionPixelSize(R.dimen.nativeInputMenuWidth),
            LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            elevation = resources.getDimension(R.dimen.modelPickerMenuElevation)
            isOutsideTouchable = true
            setOnDismissListener { popupWindow = null }
        }

        if (supportsImages) {
            addMenuItem(
                container = container,
                iconRes = com.duckduckgo.mobile.android.R.drawable.ic_camera_24,
                titleRes = R.string.attachmentTakePhoto,
            ) {
                popup.dismiss()
                host?.showAttachmentChooser(true)
                onCameraCaptureRequested?.invoke(buildImagePickerCallback())
            }

            addMenuItem(
                container = container,
                iconRes = com.duckduckgo.mobile.android.R.drawable.ic_image_24,
                titleRes = R.string.attachmentAttachPhoto,
            ) {
                popup.dismiss()
                host?.showAttachmentChooser(true)
                onFilePickerRequested?.invoke(buildImagePickerCallback(), listOf("image/*"))
            }
        }

        if (supportedFileTypes.isNotEmpty()) {
            addMenuItem(
                container = container,
                iconRes = R.drawable.ic_folder_24,
                titleRes = R.string.attachmentAttachFile,
            ) {
                popup.dismiss()
                host?.showAttachmentChooser(true)
                onFilePickerRequested?.invoke(buildFilePickerCallback(), supportedFileTypes)
            }
        }

        popupWindow = popup
        showAtPosition(popup)
    }

    private fun addMenuItem(container: LinearLayout, iconRes: Int, titleRes: Int, onClick: () -> Unit) {
        val row = LayoutInflater.from(context).inflate(R.layout.view_options_menu_item, container, false)
        row.minimumHeight = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.popupMenuItemHeight)
        row.findViewById<ImageView>(R.id.optionsMenuItemIcon).setImageResource(iconRes)
        row.findViewById<DaxTextView>(R.id.optionsMenuItemTitle).setText(titleRes)
        row.findViewById<DaxTextView>(R.id.optionsMenuItemSubtitle).visibility = GONE
        row.findViewById<ImageView>(R.id.optionsMenuItemTrailingIcon).visibility = GONE
        row.setOnClickListener { onClick() }
        container.addView(row)
    }

    private fun showAtPosition(popup: PopupWindow) {
        val button = getChildAt(0) ?: this
        val loc = IntArray(2).also { button.getLocationOnScreen(it) }
        popup.showAtLocation(rootView, Gravity.TOP or Gravity.START, loc[0], loc[1])
    }

    private fun dismissPopup() {
        popupWindow?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        popupWindow = null
    }

    private fun buildImagePickerCallback(): ValueCallback<Array<Uri>> = ValueCallback { uris ->
        val list = uris?.toList()
        if (!list.isNullOrEmpty()) viewModel?.onImagesPicked(list)
    }

    private fun buildFilePickerCallback(): ValueCallback<Array<Uri>> = ValueCallback { uris ->
        val list = uris?.toList()
        if (!list.isNullOrEmpty()) viewModel?.onFilesPicked(list)
    }
}
