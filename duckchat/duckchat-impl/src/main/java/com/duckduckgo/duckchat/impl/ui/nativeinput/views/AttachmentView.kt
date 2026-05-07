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
import android.view.View
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
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.Action
import com.duckduckgo.duckchat.impl.ui.AttachmentViewModel
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.ImageAttachment
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
    private var limitErrorView: TextView? = null

    init {
        val iconSize = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_attach_16)
        }
        addView(icon)
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

    fun getImageAttachmentsJson(): JSONArray? = viewModel?.getImageAttachmentsJson()

    fun clearAttachments() = viewModel?.clearAttachments()

    fun clearAttachmentsForNewChat() = viewModel?.clearAttachmentsForNewChat()

    fun setDuckAiMode(enabled: Boolean) = viewModel?.setDuckAiMode(enabled)

    private fun setupContainerViews(container: FrameLayout, viewModel: AttachmentViewModel) {
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

        val scroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            isHorizontalScrollBarEnabled = false
        }
        layout.addView(scroll)

        val imagesContainer = ImageAttachmentsContainerView(context)
        scroll.addView(imagesContainer)
        imagesContainer.onAttachmentRemoved = { id -> viewModel.removeImageAttachment(id) }
        imageAttachmentsContainer = imagesContainer

        val dp = resources.displayMetrics.density
        val errorView = TextView(context).apply {
            val pad = (12 * dp).toInt()
            setPadding(pad, (4 * dp).toInt(), pad, (4 * dp).toInt())
            setTextColor(resources.getColor(com.duckduckgo.mobile.android.R.color.red50, null))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            visibility = View.GONE
        }
        layout.addView(errorView)
        limitErrorView = errorView
    }

    private fun applyState(state: AttachmentViewModel.AttachmentState, container: FrameLayout) {
        val imagesView = imageAttachmentsContainer ?: return

        val stateIds = state.images.map { it.id }.toSet()
        val containerIds = imagesView.getAttachmentIds().toSet()
        (containerIds - stateIds).forEach { id -> imagesView.removeAttachmentById(id) }
        (stateIds - containerIds).forEach { id ->
            state.images.find { it.id == id }?.let { imagesView.addAttachment(it) }
        }

        container.isVisible = state.hasAttachments

        limitErrorView?.text = state.imageLimitError
        limitErrorView?.visibility = if (state.imageLimitError != null) VISIBLE else GONE

        onAction?.invoke(Action.AttachmentStateChanged(state.hasAttachments, state.imageLimitError != null, state.supportsUpload))
    }

    private fun showChooserDialog() {
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
            .addEventListener(object : ActionBottomSheetDialog.EventListener() {
                private var pickerLaunched = false

                override fun onPrimaryItemClicked() {
                    pickerLaunched = true
                    val callback = ValueCallback<Array<Uri>> { uris ->
                        val list = uris?.toList()
                        if (!list.isNullOrEmpty()) {
                            viewModel?.onImagesPicked(list)
                        }
                    }
                    onFilePickerRequested?.invoke(callback, listOf("image/*"))
                }

                override fun onSecondaryItemClicked() {
                    pickerLaunched = true
                    val callback = ValueCallback<Array<Uri>> { uris ->
                        val list = uris?.toList()
                        if (!list.isNullOrEmpty()) {
                            viewModel?.onImagesPicked(list)
                        }
                    }
                    onCameraCaptureRequested?.invoke(callback)
                }

                override fun onBottomSheetDismissed() {
                    if (!pickerLaunched) {
                        onAction?.invoke(Action.ShowAttachmentChooser(false))
                    }
                }
            }).show()
    }
}
