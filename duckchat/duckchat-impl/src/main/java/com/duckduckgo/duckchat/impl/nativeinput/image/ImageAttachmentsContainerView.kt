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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.duckduckgo.duckchat.impl.R

class ImageAttachmentsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val attachments = mutableListOf<ImageAttachment>()
    var onAttachmentRemoved: ((ImageAttachment) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    fun addAttachment(attachment: ImageAttachment) {
        attachments.add(attachment)
        addThumbnailView(attachment)
    }

    fun removeAttachment(attachment: ImageAttachment) {
        val index = attachments.indexOfFirst { it.id == attachment.id }
        if (index >= 0) {
            attachments.removeAt(index)
            removeViewAt(index)
        }
    }

    fun getAttachments(): List<ImageAttachment> = attachments.toList()

    fun clearAttachments() {
        attachments.forEach { it.bitmap.recycle() }
        attachments.clear()
        removeAllViews()
    }

    fun attachmentCount(): Int = attachments.size

    private fun addThumbnailView(attachment: ImageAttachment) {
        val thumbnailView = LayoutInflater.from(context).inflate(R.layout.view_image_attachment_thumbnail, this, false)
        val image = thumbnailView.findViewById<ImageView>(R.id.thumbnailImage)
        val removeButton = thumbnailView.findViewById<ImageView>(R.id.thumbnailRemove)
        image.setImageBitmap(attachment.bitmap)
        removeButton.setOnClickListener {
            removeAttachment(attachment)
            onAttachmentRemoved?.invoke(attachment)
        }
        addView(thumbnailView)
    }
}
