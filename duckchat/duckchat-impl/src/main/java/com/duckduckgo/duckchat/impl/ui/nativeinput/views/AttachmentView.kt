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
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ImageAttachmentsContainerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SuppressLint("ViewConstructor")
class AttachmentView(
    context: Context,
    private val attachmentHandler: AttachmentHandler,
) : FrameLayout(context) {

    private var attachmentsLayout: LinearLayout? = null
    private var imageAttachmentsContainer: ImageAttachmentsContainerView? = null
    private var imageUploadLimitJob: Job? = null
    private var wired = false

    init {
        setTag(R.id.attachButtonContainer, attachmentHandler)
        val iconSize = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_attach_16)
        }
        addView(icon)
        setOnClickListener { attachmentHandler.showAttachmentChooser() }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1.0f else 0.4f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!wired) {
            val attachmentsContainer = (parent as? View)?.rootView
                ?.findViewById<FrameLayout>(R.id.attachmentsContainer)
            if (attachmentsContainer != null) {
                wireToAttachmentsContainer(attachmentsContainer)
                wired = true
            }
        }
    }

    private fun wireToAttachmentsContainer(attachmentsContainer: FrameLayout) {
        setupAttachmentViews(attachmentsContainer)
        wireHandlerCallbacks(attachmentsContainer)
        observeImageUploadLimit()
    }

    private fun setupAttachmentViews(attachmentsContainer: FrameLayout) {
        if (attachmentsLayout != null) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
        attachmentsContainer.addView(layout)
        attachmentsLayout = layout

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
        imagesContainer.onAttachmentRemoved = { attachment ->
            attachmentHandler.removeAttachment(attachment.id)
            attachmentHandler.updateImageCount(attachmentHandler.getImageAttachments().size)
            if (!attachmentHandler.hasAttachments()) {
                attachmentsContainer.isVisible = false
            }
        }
        imageAttachmentsContainer = imagesContainer
    }

    private fun wireHandlerCallbacks(attachmentsContainer: FrameLayout) {
        attachmentHandler.onImageAttachmentAdded = { attachment ->
            imageAttachmentsContainer?.addAttachment(attachment)
            attachmentsContainer.isVisible = true
            attachmentHandler.updateImageCount(attachmentHandler.getImageAttachments().size)
        }
        attachmentHandler.onImageLimitError = { message ->
            showAttachmentLimitError(message)
        }
        attachmentHandler.onImageLimitErrorClear = {
            hideAttachmentLimitError()
        }
        attachmentHandler.onAttachmentsCleared = {
            imageAttachmentsContainer?.clearAttachments()
            attachmentsContainer.isVisible = false
        }
    }

    private fun observeImageUploadLimit() {
        imageUploadLimitJob?.cancel()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        imageUploadLimitJob = attachmentHandler.imageUploadLimitReached
            .onEach { reached ->
                attachmentHandler.conversationImageLimitReached = reached
                if (!reached) attachmentHandler.resetConversationCounts()
            }
            .launchIn(scope)
    }

    private fun showAttachmentLimitError(message: String) {
        val layout = attachmentsLayout ?: return
        (layout.parent as? View)?.isVisible = true
        val errorView = layout.findViewWithTag<TextView>("attachmentError")
            ?: TextView(context).apply {
                tag = "attachmentError"
                val dp = resources.displayMetrics.density
                setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
                setTextColor(resources.getColor(com.duckduckgo.mobile.android.R.color.red50, null))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                layout.addView(this)
            }
        errorView.text = message
        errorView.visibility = VISIBLE
    }

    private fun hideAttachmentLimitError() {
        attachmentsLayout?.findViewWithTag<TextView>("attachmentError")?.visibility = GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageUploadLimitJob?.cancel()
        imageUploadLimitJob = null
    }
}
