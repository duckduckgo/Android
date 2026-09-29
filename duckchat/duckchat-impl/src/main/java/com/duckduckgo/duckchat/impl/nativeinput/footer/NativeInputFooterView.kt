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
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

open class NativeInputFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private var bindingScope: CoroutineScope? = null
    private var stateSource: Flow<NativeInputFooterCoordinator.State>? = null
    private var bindingJob: Job? = null
    private var selectedView: View? = null
    private var surfaceVisible = true
    private var exitAnimationRunning = false
    private var blocksComposer = false
    private var onBlocksComposerChanged: ((Boolean) -> Unit)? = null

    init {
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
        val view = state.view
        if (selectedView !== view) {
            removeAllViews()
            selectedView = view
            if (view != null) {
                (view.parent as? ViewGroup)?.removeView(view)
                addView(view)
            }
        }
        updateBlocksComposer(view != null && state.blocksComposer)
        updateVisibility()
    }

    private fun updateBlocksComposer(blocked: Boolean) {
        if (blocksComposer == blocked) return
        blocksComposer = blocked
        onBlocksComposerChanged?.invoke(blocked)
    }

    private fun updateVisibility() {
        visibility = if (surfaceVisible && selectedView != null && !exitAnimationRunning) VISIBLE else GONE
        (parent as? NativeInputFooterDockLayout)?.onFooterVisibilityChanged()
    }
}
