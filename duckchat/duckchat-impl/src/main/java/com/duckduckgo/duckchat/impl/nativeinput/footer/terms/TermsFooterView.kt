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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView

class TermsFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val message: DaxTextView

    init {
        val cornerRadius = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)
        shapeAppearanceModel = shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(0f)
            .setTopRightCornerSize(0f)
            .setBottomLeftCornerSize(cornerRadius)
            .setBottomRightCornerSize(cornerRadius)
            .build()
        cardElevation = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.keyline_0)
        setCardBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface))
        strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorOmnibarAccent)
        strokeWidth = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.omnibarOutlineWidth)
        useCompatPadding = false
        LayoutInflater.from(context).inflate(R.layout.view_terms_footer, this, true)
        message = findViewById(R.id.termsFooterMessage)
    }

    fun render(onLinkClick: (String) -> Unit) {
        message.addClickableSpan(
            textSequence = context.getText(R.string.duckChatTermsFooterMessage) as SpannedString,
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
