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

package com.duckduckgo.app.browser.longpress

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.text.DaxTextView

/**
 * Builds and shows the branded long-press context menu for a [LongPressTarget], adapting its
 * rows to the current browser mode (see [longPressMenuConfigFor]). Each row delegates to
 * [Listener]; the listener owns pixels and action dispatch so this class is presentation-only.
 */
class LongPressPopupMenu(
    private val layoutInflater: LayoutInflater,
    private val target: LongPressTarget,
    private val isFireMode: Boolean,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpenInNewTab(url: String)
        fun onOpenInFireTab(url: String)
        fun onOpenInBackgroundTab(url: String)
        fun onCopyLinkAddress(url: String)
        fun onCopyLinkText()
        fun onShareLink(url: String)
        fun onDownloadImage(imageUrl: String)
        fun onOpenImageInNewTab(imageUrl: String)
    }

    fun show(
        rootView: View,
        screenX: Int,
        screenY: Int,
    ): Boolean {
        val config = longPressMenuConfigFor(target.type, isFireMode) ?: return false
        val layoutRes = when (config.shape) {
            LongPressMenuShape.LINK -> R.layout.popup_long_press_link_menu
            LongPressMenuShape.IMAGE -> R.layout.popup_long_press_image_menu
            LongPressMenuShape.IMAGE_LINK -> R.layout.popup_long_press_image_link_menu
        }
        val popup = PopupMenu(layoutInflater, layoutRes)
        val content = popup.contentView

        content.findViewById<DaxTextView>(R.id.longPressUrlHeader)?.text = target.url ?: target.imageUrl ?: ""

        val url = target.url
        val imageUrl = target.imageUrl

        // Link rows (present on LINK + IMAGE_LINK)
        content.findViewById<PopupMenuItemView>(R.id.longPressOpenInNewTab)?.let { primary ->
            if (config.primaryRowIsFireTab) {
                primary.setPrimaryText(primary.context.getString(R.string.openInFireTab))
            }
            popup.onMenuItemClicked(primary) {
                url?.let { if (config.primaryRowIsFireTab) listener.onOpenInFireTab(it) else listener.onOpenInNewTab(it) }
            }
        }
        content.findViewById<PopupMenuItemView>(R.id.longPressOpenInBackgroundTab)?.let { row ->
            popup.onMenuItemClicked(row) { url?.let(listener::onOpenInBackgroundTab) }
        }
        content.findViewById<View>(R.id.longPressFireTabDivider)?.isVisible = config.showDedicatedFireTabRow
        content.findViewById<PopupMenuItemView>(R.id.longPressOpenInFireTab)?.let { row ->
            row.isVisible = config.showDedicatedFireTabRow
            popup.onMenuItemClicked(row) { url?.let(listener::onOpenInFireTab) }
        }
        content.findViewById<PopupMenuItemView>(R.id.longPressCopyLinkAddress)?.let { row ->
            popup.onMenuItemClicked(row) { url?.let(listener::onCopyLinkAddress) }
        }
        content.findViewById<PopupMenuItemView>(R.id.longPressCopyLinkText)?.let { row ->
            popup.onMenuItemClicked(row) { listener.onCopyLinkText() }
        }
        content.findViewById<PopupMenuItemView>(R.id.longPressShareLink)?.let { row ->
            popup.onMenuItemClicked(row) { url?.let(listener::onShareLink) }
        }

        // Image rows (present on IMAGE + IMAGE_LINK)
        content.findViewById<PopupMenuItemView>(R.id.longPressDownloadImage)?.let { row ->
            popup.onMenuItemClicked(row) { imageUrl?.let(listener::onDownloadImage) }
        }
        content.findViewById<PopupMenuItemView>(R.id.longPressOpenImageInNewTab)?.let { row ->
            popup.onMenuItemClicked(row) { imageUrl?.let(listener::onOpenImageInNewTab) }
        }

        popup.applyRoundedRippleCornersToVisibleItems()
        popup.showAtTouchPoint(rootView, screenX, screenY)
        return true
    }
}
