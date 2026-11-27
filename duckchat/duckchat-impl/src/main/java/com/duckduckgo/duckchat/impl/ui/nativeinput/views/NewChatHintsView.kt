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
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NewChatHint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ViewScope::class)
class NewChatHintsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    var tabId: String? = null

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewChatHintsViewModel::class.java]
    }

    private var stateJob: Job? = null
    private val row = LinearLayout(context)
    private val content = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        val inset = resources.getDimensionPixelSize(CommonR.dimen.keyline_4)
        setPaddingRelative(inset, 0, inset, 0)
        addView(row)
    }

    init {
        isVisible = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        val tabId = tabId ?: return
        stateJob?.cancel()
        stateJob = viewModel.viewState(tabId)
            .onEach { hints ->
                render(hints, tabId)
                show(hints.isNotEmpty())
            }.launchIn(scope)
    }

    // The host fades the hints out with the input while they stay VISIBLE; invisible chips must not take taps.
    // The tab pager intercepts horizontal drags before the scroll view claims them, so claim them up front while the row overflows.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (alpha <= 0f) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && content.overflowsHorizontally()) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun HorizontalScrollView.overflowsHorizontally() = canScrollHorizontally(-1) || canScrollHorizontally(1)

    override fun onDetachedFromWindow() {
        stateJob?.cancel()
        stateJob = null
        settleChips()
        super.onDetachedFromWindow()
    }

    private fun show(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        if (visible) animateChipsIn() else settleChips()
    }

    // The overshoot stands in for a damping 0.6 / stiffness 800 spring to avoid a dynamicanimation dependency; alpha reaches 1 early on the same curve.
    private fun animateChipsIn() {
        settleChips()
        val offset = CHIP_SLIDE_OFFSET_DP.toPx().toFloat() * if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        row.children.forEachIndexed { index, chip ->
            val delay = index * CHIP_STAGGER_MS
            chip.translationX = offset
            chip.alpha = 0f
            chip.animate()
                .translationX(0f)
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(CHIP_SLIDE_MS)
                .setInterpolator(OvershootInterpolator(CHIP_OVERSHOOT_TENSION))
                .start()
        }
    }

    private fun settleChips() {
        row.children.forEach { chip ->
            chip.animate().cancel()
            chip.translationX = 0f
            chip.alpha = 1f
        }
    }

    private fun render(hints: List<NewChatHint>, tabId: String) {
        row.removeAllViews()
        hints.forEachIndexed { index, hint ->
            val label = LayoutInflater.from(context).inflate(R.layout.item_new_chat_hint, row, false) as DaxTextView
            val (text, icon) = when (hint) {
                NewChatHint.DOCUMENTS -> R.string.newChatHintDocuments to R.drawable.ic_document_new_24
                NewChatHint.PHOTO -> R.string.newChatHintPhoto to CommonR.drawable.ic_camera_24
                NewChatHint.IMAGE_GENERATION -> R.string.newChatHintImage to CommonR.drawable.ic_image_24
            }
            label.setText(text)
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            label.setOnClickListener {
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch { viewModel.onHintTapped(hint, position = index + 1, tabId) }
            }
            row.addView(label)
        }
    }

    private companion object {
        const val CHIP_SLIDE_OFFSET_DP = 96
        const val CHIP_STAGGER_MS = 40L
        const val CHIP_SLIDE_MS = 300L
        const val CHIP_OVERSHOOT_TENSION = 1.6f
    }
}
