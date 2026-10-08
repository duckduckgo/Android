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
package com.duckduckgo.duckchat.impl.nativeinput.footer.terms

import android.content.Context
import android.text.SpannedString
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

class TermsFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val message: DaxTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_terms_footer, this, true)
        message = findViewById(R.id.termsFooterMessage)
    }

    fun render(onLinkClick: (String) -> Unit) {
        message.addClickableSpan(
            textSequence = SpannedString(
                TextUtils.expandTemplate(
                    context.getText(R.string.duckChatTermsFooterMessage),
                    context.getText(R.string.duckChatSubmitAskLabel),
                ),
            ),
            spans = listOf(
                TERMS_ANNOTATION to clickable { onLinkClick(TERMS_URL) },
                PRIVACY_ANNOTATION to clickable { onLinkClick(PRIVACY_URL) },
            ),
        )
    }

    private fun clickable(onClick: () -> Unit) = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) = onClick()
    }

    private companion object {
        const val TERMS_ANNOTATION = "terms_link"
        const val PRIVACY_ANNOTATION = "privacy_link"
        const val TERMS_URL = "https://duckduckgo.com/duckai/privacy-terms"
        const val PRIVACY_URL = "https://duckduckgo.com/duckai/privacy-terms"
    }
}
