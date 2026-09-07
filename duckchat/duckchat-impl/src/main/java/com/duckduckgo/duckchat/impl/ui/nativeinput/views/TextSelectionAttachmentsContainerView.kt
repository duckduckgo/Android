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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.TextSelectionAttachment

class TextSelectionAttachmentsContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var attachments: List<TextSelectionAttachment> = emptyList()
    var onAttachmentRemoved: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    fun current(): List<TextSelectionAttachment> = attachments

    fun render(attachments: List<TextSelectionAttachment>) {
        this.attachments = attachments
        removeAllViews()
        attachments.forEach { attachment ->
            val itemView = LayoutInflater.from(context).inflate(R.layout.view_page_context_attachment_item, this, false)
            itemView.findViewById<DaxTextView>(R.id.pageContextTitle).text = attachment.text
            itemView.findViewById<ImageView>(R.id.pageContextRemove).setOnClickListener {
                onAttachmentRemoved?.invoke(attachment.id)
            }
            addView(itemView)
        }
    }
}
