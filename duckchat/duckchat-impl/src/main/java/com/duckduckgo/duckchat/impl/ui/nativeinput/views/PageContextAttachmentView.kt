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
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.attachment.PageContextAttachment

class PageContextAttachmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var attachment: PageContextAttachment? = null
    private var faviconView: ImageView? = null

    init {
        orientation = HORIZONTAL
        clipChildren = false
        clipToPadding = false
        gone()
    }

    fun current(): PageContextAttachment? = attachment

    /**
     * The favicon [ImageView] for the currently shown chip, or null when nothing is shown. The host
     * loads the page favicon into it; until then it keeps its default placeholder icon.
     */
    fun faviconView(): ImageView? = faviconView

    fun show(attachment: PageContextAttachment, onRemoved: () -> Unit) {
        this.attachment = attachment
        removeAllViews()
        val itemView = LayoutInflater.from(context).inflate(R.layout.view_page_context_attachment_item, this, false)
        itemView.findViewById<DaxTextView>(R.id.pageContextTitle).text = attachment.title
        itemView.findViewById<ImageView>(R.id.pageContextRemove).setOnClickListener { onRemoved() }
        faviconView = itemView.findViewById(R.id.pageContextFavicon)
        addView(itemView)
        show()
    }

    fun hide() {
        attachment = null
        faviconView = null
        removeAllViews()
        gone()
    }
}
