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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

/** Appended to rows that open a follow-up step, the platform convention for "this isn't a pick". */
private const val FOLLOW_UP_SUFFIX = "…"

/**
 * A row shared by the Duck.ai model and reasoning pickers: leading icon, title with an optional
 * subline, and a trailing tick for the current selection. The ADS [PopupMenuItemView] carries no
 * secondary text, which is why these pickers own their row.
 *
 * Set [opensFollowUp] for a row that leads somewhere else, such as a gated model that opens an
 * upsell, rather than selecting outright.
 */
internal fun pickerMenuItem(
    parent: ViewGroup,
    title: String,
    @DrawableRes leadingIconRes: Int,
    subtitle: String? = null,
    selected: Boolean = false,
    opensFollowUp: Boolean = false,
    onClick: () -> Unit,
): View {
    val item = LayoutInflater.from(parent.context).inflate(R.layout.view_picker_menu_item, parent, false)

    item.findViewById<ImageView>(R.id.pickerMenuItemLeadingIcon)
        .setImageDrawable(AppCompatResources.getDrawable(parent.context, leadingIconRes))

    item.findViewById<DaxTextView>(R.id.pickerMenuItemTitle).text =
        if (opensFollowUp) "$title$FOLLOW_UP_SUFFIX" else title

    item.findViewById<DaxTextView>(R.id.pickerMenuItemSubtitle).apply {
        text = subtitle
        isVisible = !subtitle.isNullOrEmpty()
    }

    item.findViewById<ImageView>(R.id.pickerMenuItemTrailingIcon).apply {
        setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
        // Invisible rather than gone so titles stay aligned across selected and unselected rows.
        visibility = if (selected) View.VISIBLE else View.INVISIBLE
    }

    item.setOnClickListener { onClick() }
    return item
}
