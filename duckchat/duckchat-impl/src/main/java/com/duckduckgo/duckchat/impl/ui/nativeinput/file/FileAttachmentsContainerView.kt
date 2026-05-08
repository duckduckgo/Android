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

package com.duckduckgo.duckchat.impl.ui.nativeinput.file

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

class FileAttachmentsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val attachments = mutableListOf<FileAttachment>()
    var onAttachmentRemoved: ((FileAttachment) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    fun addAttachment(attachment: FileAttachment) {
        attachments.add(attachment)
        addFileItemView(attachment)
    }

    fun removeAttachment(attachment: FileAttachment) {
        val index = attachments.indexOfFirst { it.id == attachment.id }
        if (index >= 0) {
            attachments.removeAt(index)
            removeViewAt(index)
        }
    }

    fun getAttachments(): List<FileAttachment> = attachments.toList()

    fun clearAttachments() {
        attachments.clear()
        removeAllViews()
    }

    fun attachmentCount(): Int = attachments.size

    fun totalSizeBytes(): Long = attachments.sumOf { it.sizeBytes }

    private fun addFileItemView(attachment: FileAttachment) {
        val itemView = LayoutInflater.from(context).inflate(R.layout.view_file_attachment_item, this, false)
        val fileNameText = itemView.findViewById<DaxTextView>(R.id.fileName)
        val removeButton = itemView.findViewById<ImageView>(R.id.fileRemove)
        fileNameText.text = attachment.fileName
        removeButton.setOnClickListener {
            removeAttachment(attachment)
            onAttachmentRemoved?.invoke(attachment)
        }
        addView(itemView)
    }
}
