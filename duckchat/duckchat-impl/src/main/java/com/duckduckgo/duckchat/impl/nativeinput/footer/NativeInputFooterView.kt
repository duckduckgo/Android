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

package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NativeInputFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {
    private var bindingScope: CoroutineScope? = null
    private var stateSource: Flow<NativeInputFooterCoordinator.State>? = null
    private var bindingJob: Job? = null
    private val card = MaterialCardView(context)
    private val rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var renderedFooters: List<NativeInputFooter> = emptyList()
    private var displayedFooters: List<NativeInputFooter> = emptyList()
    private var surfaceVisible = true
    private var exitAnimationRunning = false
    private var blocksComposer = false
    private var onBlocksComposerChanged: ((Boolean) -> Unit)? = null

    init {
        val cornerRadius = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.largeShapeCornerRadius)
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(0f)
            .setTopRightCornerSize(0f)
            .setBottomLeftCornerSize(cornerRadius)
            .setBottomRightCornerSize(cornerRadius)
            .build()
        card.cardElevation = resources.getDimension(com.duckduckgo.mobile.android.R.dimen.keyline_0)
        card.setCardBackgroundColor(context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface))
        card.strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorOmnibarAccent)
        card.strokeWidth = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.omnibarOutlineWidth)
        card.useCompatPadding = false
        // The host stays flat and the card is its child: the dock layout reads the card, and a flat host keeps the
        // footer behind the input card, which an elevated host would not.
        // Rows are tucked under the input card; the dock layout positions this view against its bottom edge.
        rowsContainer.setPaddingRelative(0, resources.getDimensionPixelSize(R.dimen.nativeInputFooterOverlap), 0, 0)
        card.addView(rowsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        visibility = GONE
    }

    fun bind(
        scope: CoroutineScope,
        state: Flow<NativeInputFooterCoordinator.State>,
        onBlocksComposerChanged: (Boolean) -> Unit = {},
    ) {
        bindingJob?.cancel()
        bindingJob = null
        // Release the previous widget's footer-owned lock before replacing its callback.
        render(NativeInputFooterCoordinator.State())
        bindingScope = scope
        stateSource = state
        this.onBlocksComposerChanged = onBlocksComposerChanged
        if (isAttachedToWindow) {
            startCollecting()
        }
    }

    fun unbind() {
        bindingJob?.cancel()
        bindingJob = null
        bindingScope = null
        stateSource = null
        render(NativeInputFooterCoordinator.State())
        onBlocksComposerChanged = null
    }

    fun setSurfaceVisible(visible: Boolean) {
        surfaceVisible = visible
        updateVisibility()
    }

    fun setExitAnimationRunning(running: Boolean) {
        exitAnimationRunning = running
        updateVisibility()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startCollecting()
    }

    override fun onDetachedFromWindow() {
        bindingJob?.cancel()
        bindingJob = null
        render(NativeInputFooterCoordinator.State())
        super.onDetachedFromWindow()
    }

    private fun startCollecting() {
        if (bindingJob != null) return
        val scope = bindingScope ?: return
        val source = stateSource ?: return
        bindingJob = scope.launch {
            source.collect(::render)
        }
    }

    private fun render(state: NativeInputFooterCoordinator.State) {
        val footers = state.footers
        if (renderedFooters != footers) {
            rowsContainer.removeAllViews()
            footers.forEachIndexed { index, footer ->
                val row = footer.view
                (row.parent as? ViewGroup)?.removeView(row)
                val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                if (index > 0) params.topMargin = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2)
                rowsContainer.addView(row, params)
            }
            renderedFooters = footers
        }
        updateBlocksComposer(footers.isNotEmpty() && state.blocksComposer)
        updateVisibility()
    }

    private fun updateBlocksComposer(blocked: Boolean) {
        if (blocksComposer == blocked) return
        blocksComposer = blocked
        onBlocksComposerChanged?.invoke(blocked)
    }

    private fun updateVisibility() {
        visibility = if (surfaceVisible && renderedFooters.isNotEmpty() && !exitAnimationRunning) VISIBLE else GONE
        (parent as? NativeInputFooterDockLayout)?.onFooterVisibilityChanged()
        val displayed = if (visibility == VISIBLE) renderedFooters else emptyList()
        if (displayedFooters != displayed) {
            val previous = displayedFooters
            displayedFooters = displayed
            previous.filter { it !in displayed }.forEach { it.onDisplayed(false) }
            displayed.filter { it !in previous }.forEach { it.onDisplayed(true) }
        }
    }
}
