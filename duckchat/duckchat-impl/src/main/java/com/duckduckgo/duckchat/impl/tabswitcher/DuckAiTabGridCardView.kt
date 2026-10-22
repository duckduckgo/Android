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

package com.duckduckgo.duckchat.impl.tabswitcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemCallbacks
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemContent
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState.Selection
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewDuckAiTabGridCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.duckduckgo.browser.ui.R as BrowserUiR
import com.duckduckgo.mobile.android.R as CommonR

class DuckAiTabGridCardView(
    context: Context,
    private val thumbnailLoader: suspend (fileRef: String, mode: BrowserMode) -> ByteArray?,
    private val dispatchers: DispatcherProvider,
    private val coroutineScope: CoroutineScope,
) : DuckAiTabGridItemView {

    private val binding = ViewDuckAiTabGridCardBinding.inflate(LayoutInflater.from(context), FrameLayout(context), false)
    private val thumbnailJob = ConflatedJob()
    private var boundContent: RealDuckAiTabGridItemContent? = null

    override val view: View get() = binding.root

    init {
        addExtraCloseButtonTouchArea()
        binding.root.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    (boundContent as? RealDuckAiTabGridItemContent.ImageChat)?.let { loadThumbnail(it) }
                }

                override fun onViewDetachedFromWindow(view: View) {
                    thumbnailJob.cancel()
                }
            },
        )
    }

    override fun bind(content: DuckAiTabGridItemContent, state: DuckAiTabGridItemState, callbacks: DuckAiTabGridItemCallbacks) {
        check(content is RealDuckAiTabGridItemContent) { "DuckAiTabGridItemContent must come from DuckAiTabGridItemRenderer.resolve()" }
        bindContent(content)
        bindState(state)
        binding.root.setOnClickListener { callbacks.onClick() }
        binding.selectionIndicator.setOnClickListener { callbacks.onClick() }
        binding.close.setOnClickListener { callbacks.onClose() }
    }

    private fun bindContent(content: RealDuckAiTabGridItemContent) {
        if (content == boundContent) return
        boundContent = content
        thumbnailJob.cancel()

        with(binding) {
            tabChatItemDetails.gone()
            thumbnail.gone()
            thumbnail.setImageDrawable(null)
            tabChatItemEmptyLogo.gone()
            voiceActive.gone()
            tabChatItemBranding.setText(
                if (content == RealDuckAiTabGridItemContent.Empty) BrowserUiR.string.browserMenuNewChat else R.string.duck_chat_title,
            )

            when (content) {
                is RealDuckAiTabGridItemContent.Chat -> {
                    tabChatItemTitle.text = content.title
                    tabChatItemTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
                    snippet.text = content.snippet
                    tabChatItemDetails.show()
                }
                is RealDuckAiTabGridItemContent.Thinking -> {
                    tabChatItemTitle.setText(R.string.duckAiTabCardThinkingTitle)
                    tabChatItemTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_thinking_16, 0, 0, 0)
                    snippet.text = content.title
                    tabChatItemDetails.show()
                }
                is RealDuckAiTabGridItemContent.ImageChat -> {
                    thumbnail.show()
                    loadThumbnail(content)
                }
                RealDuckAiTabGridItemContent.Empty -> {
                    tabChatItemEmptyLogo.show()
                }
                RealDuckAiTabGridItemContent.ActiveVoice -> {
                    voiceActive.show()
                }
            }
        }
    }

    private fun bindState(state: DuckAiTabGridItemState) = with(binding) {
        when (val selection = state.selection) {
            Selection.None -> {
                selectionIndicator.gone()
                close.isClickable = true
                close.show()
            }
            is Selection.Selectable -> {
                if (selection.isSelected) {
                    selectionIndicator.setImageResource(CommonR.drawable.ic_check_circle_accent_24)
                    selectionIndicator.contentDescription = root.context.getString(R.string.duck_ai_chat_history_row_selected_content_description)
                } else {
                    selectionIndicator.setImageResource(CommonR.drawable.ic_shape_circle_24)
                    selectionIndicator.contentDescription = root.context.getString(R.string.duck_ai_chat_history_row_unselected_content_description)
                }
                selectionIndicator.show()
                close.isClickable = false
                close.gone()
            }
        }
    }

    private fun addExtraCloseButtonTouchArea() {
        val closeButton = binding.close
        val parent = closeButton.parent as View
        parent.post {
            val extraSpace = EXTRA_CLOSE_BUTTON_TOUCH_AREA_DP.toPx()
            val touchableArea = Rect()
            closeButton.getHitRect(touchableArea)
            touchableArea.inset(-extraSpace, -extraSpace)
            parent.touchDelegate = TouchDelegate(touchableArea, closeButton)
        }
    }

    // A missing or undecodable image falls back to the empty look.
    private fun loadThumbnail(content: RealDuckAiTabGridItemContent.ImageChat) {
        if (binding.thumbnail.drawable != null) return
        thumbnailJob += coroutineScope.launch(dispatchers.main()) {
            val bitmap = withContext(dispatchers.io()) {
                thumbnailLoader(content.fileRef, content.mode)?.let { decodeSampled(it) }
            }
            if (boundContent != content) return@launch
            if (bitmap != null) {
                binding.thumbnail.setImageBitmap(bitmap)
            } else {
                binding.thumbnail.gone()
                binding.tabChatItemEmptyLogo.show()
            }
        }
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_PX && bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_PX) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private companion object {
        // Covers the card's preview slot at high densities with room for the centerCrop.
        const val THUMBNAIL_MAX_PX = 512
        const val EXTRA_CLOSE_BUTTON_TOUCH_AREA_DP = 6
    }
}
