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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Icon
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Severity
import com.google.android.material.card.MaterialCardView

class UsageLimitFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val ring: UsageRingView
    private val alert: ImageView
    private val title: DaxTextView
    private val resetText: DaxTextView
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
        LayoutInflater.from(context).inflate(R.layout.view_usage_limit_footer, this, true)
        ring = findViewById(R.id.usageLimitFooterRing)
        alert = findViewById(R.id.usageLimitFooterAlert)
        title = findViewById<DaxTextView>(R.id.usageLimitFooterTitle).apply {
            // The design system has no bold caption typography, so bold the caption in place.
            setTypeface(typeface, Typeface.BOLD)
        }
        resetText = findViewById(R.id.usageLimitFooterResetText)
        dismiss = findViewById(R.id.usageLimitFooterDismiss)
    }

    fun render(
        message: UsageLimitFooterMessage,
        onDismiss: () -> Unit,
    ) {
        title.text = message.title
        resetText.text = message.resetText
        when (val icon = message.icon) {
            is Icon.Ring -> {
                ring.visibility = VISIBLE
                alert.visibility = GONE
                ring.progress = icon.progress
                ring.setColors(
                    track = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorLines),
                    progress = context.getColorFromAttr(icon.severity.colorAttr()),
                )
            }
            Icon.Alert -> {
                ring.visibility = GONE
                alert.visibility = VISIBLE
            }
        }
        dismiss.visibility = if (message.dismissible) VISIBLE else GONE
        dismiss.setOnClickListener { onDismiss() }
    }

    private fun Severity.colorAttr(): Int = when (this) {
        Severity.INFO -> com.duckduckgo.mobile.android.R.attr.daxColorPrimaryIcon
        Severity.WARNING -> com.duckduckgo.mobile.android.R.attr.daxColorAccentYellow
        Severity.CRITICAL -> com.duckduckgo.mobile.android.R.attr.daxColorDestructive
    }
}
