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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView

class HighUsageModelFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val message: DaxTextView
    private val dismiss: ImageView

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
        LayoutInflater.from(context).inflate(R.layout.view_high_usage_model_footer, this, true)
        message = findViewById(R.id.highUsageModelFooterMessage)
        dismiss = findViewById(R.id.highUsageModelFooterDismiss)
    }

    fun render(
        modelShortName: String,
        onDismiss: () -> Unit,
    ) {
        message.text = context.getString(R.string.duckChatHighUsageModelFooterMessage, modelShortName)
        dismiss.setOnClickListener { onDismiss() }
    }
}
