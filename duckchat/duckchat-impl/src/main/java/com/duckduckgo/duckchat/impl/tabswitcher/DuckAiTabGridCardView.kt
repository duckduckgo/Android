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
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemCallbacks
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemKey
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState.Selection
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewDuckAiTabGridCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import com.duckduckgo.browser.ui.R as BrowserUiR
import com.duckduckgo.mobile.android.R as CommonR

/** Where a card gets its content from; implemented by [RealDuckAiTabGridItemRenderer]. */
interface DuckAiTabGridContentSource {
    /** Content that is known without reading storage, or null when [load] has to read it. */
    fun peek(key: DuckAiTabGridItemKey): RealDuckAiTabGridItemContent?

    suspend fun load(key: DuckAiTabGridItemKey): RealDuckAiTabGridItemContent
}

class DuckAiTabGridCardView(
    context: Context,
    private val contentSource: DuckAiTabGridContentSource,
    private val dispatchers: DispatcherProvider,
    private val coroutineScope: CoroutineScope,
) : DuckAiTabGridItemView {

    private val binding = ViewDuckAiTabGridCardBinding.inflate(LayoutInflater.from(context), FrameLayout(context), false)
    private val contentJob = ConflatedJob()
    private var boundKey: DuckAiTabGridItemKey? = null
    private var boundState: DuckAiTabGridItemState? = null

    // Null while the card shows its placeholder and its content is loading.
    private var boundContent: RealDuckAiTabGridItemContent? = null

    override val view: View get() = binding.root

    init {
        addExtraCloseButtonTouchArea()
    }

    override fun bind(key: DuckAiTabGridItemKey, state: DuckAiTabGridItemState, callbacks: DuckAiTabGridItemCallbacks) {
        boundState = state
        bindState(state)
        binding.root.setOnClickListener { callbacks.onClick() }
        binding.selectionIndicator.setOnClickListener { callbacks.onClick() }
        binding.close.setOnClickListener { callbacks.onClose() }

        if (key != boundKey) {
            boundKey = key
            contentJob.cancel()
            val known = contentSource.peek(key)
            if (known != null) {
                bindContent(known)
            } else {
                showPlaceholder(state.title)
                loadContent(key)
            }
            return
        }

        when (boundContent) {
            null -> binding.tabChatItemTitle.text = state.title
            // Glide skips a request equal to the one already shown, so only a new path reloads.
            RealDuckAiTabGridItemContent.Fallback -> loadScreenshot(state.screenshotPath)
            // A reply may have been stored since, and Thinking is never cached, so each rebind is a chance to pick it up.
            is RealDuckAiTabGridItemContent.Thinking -> if (!contentJob.isActive) loadContent(key)
            else -> Unit
        }
    }

    // Not cancelled when the card scrolls away: the read is short and its result is cached for when the card comes back.
    private fun loadContent(key: DuckAiTabGridItemKey) {
        contentJob += coroutineScope.launch(dispatchers.main()) {
            val content = contentSource.load(key)
            if (boundKey != key) return@launch
            bindContent(content)
        }
    }

    private fun showPlaceholder(title: String) {
        boundContent = null
        resetBody()
        binding.tabChatItemBranding.setText(R.string.duck_chat_title)
        bindDetails(title)
    }

    private fun resetBody() = with(binding) {
        tabChatItemDetails.gone()
        tabChatItemType.gone()
        snippet.gone()
        thumbnail.gone()
        thumbnail.setImageDrawable(null)
        tabChatItemVoice.gone()
        tabChatItemEmptyLogo.gone()
        Glide.with(tabChatItemScreenshot).clear(tabChatItemScreenshot)
        tabChatItemScreenshot.gone()
    }

    private fun bindContent(content: RealDuckAiTabGridItemContent) {
        boundContent = content
        resetBody()

        with(binding) {
            tabChatItemBranding.setText(
                if (content == RealDuckAiTabGridItemContent.Empty) BrowserUiR.string.browserMenuNewChat else R.string.duck_chat_title,
            )

            when (content) {
                is RealDuckAiTabGridItemContent.Chat -> {
                    bindDetails(content.title)
                    bindSnippet(content.snippet)
                    bindChatType(R.string.duckAiTabCardChatTypeLabel, R.drawable.ic_chat_16)
                }
                is RealDuckAiTabGridItemContent.Thinking -> {
                    bindDetails(root.context.getString(R.string.duckAiTabCardThinkingTitle), titleIcon = R.drawable.ic_thinking_16)
                    bindSnippet(content.title)
                    bindChatType(R.string.duckAiTabCardChatTypeLabel, R.drawable.ic_chat_16)
                }
                is RealDuckAiTabGridItemContent.ImageChat -> {
                    bindDetails(content.title)
                    bindThumbnail(content.thumbnail)
                    bindChatType(R.string.duckAiTabCardChatTypeLabel, R.drawable.ic_chat_16)
                }
                is RealDuckAiTabGridItemContent.VoiceTranscript -> {
                    bindDetails(content.title)
                    bindSnippet(content.transcript)
                    bindChatType(R.string.duckAiTabCardTranscriptTypeLabel, R.drawable.ic_ai_chat_voice_16)
                }
                RealDuckAiTabGridItemContent.Empty -> {
                    tabChatItemEmptyLogo.show()
                }
                RealDuckAiTabGridItemContent.ActiveVoice -> {
                    tabChatItemVoice.show()
                }
                RealDuckAiTabGridItemContent.Fallback -> {
                    loadScreenshot(boundState?.screenshotPath)
                }
            }
        }
    }

    private fun bindDetails(title: String, @DrawableRes titleIcon: Int = 0) = with(binding) {
        tabChatItemTitle.text = title
        tabChatItemTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(titleIcon, 0, 0, 0)
        tabChatItemDetails.show()
    }

    private fun bindSnippet(text: String) = with(binding.snippet) {
        this.text = text
        show()
    }

    private fun bindChatType(@StringRes label: Int, @DrawableRes icon: Int) = with(binding.tabChatItemType) {
        setChipText(label)
        setChipIcon(icon)
        show()
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

    // A missing or undecodable image shows the Duck.ai logo in its place, keeping the title and label.
    private fun bindThumbnail(bitmap: Bitmap?) = with(binding.thumbnail) {
        if (bitmap != null) {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        } else {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(CommonR.drawable.duckduckgo_duckai_96)
        }
        show()
    }

    // Loaded the same way as the host's own screenshot card, so a tab whose chat can't be read looks as it did before.
    private fun loadScreenshot(path: String?) = with(binding) {
        val glide = Glide.with(tabChatItemScreenshot)
        if (path == null) {
            glide.clear(tabChatItemScreenshot)
            tabChatItemScreenshot.gone()
            tabChatItemEmptyLogo.show()
            return@with
        }
        tabChatItemEmptyLogo.gone()
        tabChatItemScreenshot.show()
        val cornerRadius = root.context.resources.getDimensionPixelSize(CommonR.dimen.smallShapeCornerRadius)
        glide.load(File(path))
            .transition(DrawableTransitionOptions.withCrossFade())
            .transform(FitAndClipBottom, RoundedCorners(cornerRadius))
            .into(tabChatItemScreenshot)
    }

    // A single stateless instance with a fixed cache key, so Glide's memory cache matches it across binds.
    private object FitAndClipBottom : Transformation<Bitmap> {
        private const val ID = "com.duckduckgo.duckchat.impl.tabswitcher.FitAndClipBottom"

        override fun transform(context: Context, resource: Resource<Bitmap>, outWidth: Int, outHeight: Int): Resource<Bitmap> {
            resource.get().height = context.resources.getDimension(CommonR.dimen.gridItemPreviewHeight).toInt()
            return resource
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update(ID.toByteArray(Charsets.UTF_8))
        }
    }

    private companion object {
        const val EXTRA_CLOSE_BUTTON_TOUCH_AREA_DP = 6
    }
}
