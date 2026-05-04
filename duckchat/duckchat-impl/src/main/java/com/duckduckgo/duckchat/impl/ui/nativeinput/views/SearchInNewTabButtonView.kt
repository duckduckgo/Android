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
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout.LayoutParams
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SearchInNewTabButtonView(context: Context) : AppCompatImageView(context) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SearchInNewTabViewModel::class.java]
    }

    private var widget: NativeInputWidget? = null
    private var isModeAllowed: Boolean = false
    private var modeJob: Job? = null

    init {
        val size = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        layoutParams = LayoutParams(size, size, Gravity.TOP)
        setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
        scaleType = ScaleType.CENTER_INSIDE
        setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_find_search_24)
        contentDescription = context.getString(R.string.duckAiInputScreenSearchInNewTabContentDescription)
        setOnClickListener { onClick() }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        widget = findNativeInputWidget()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
        observeMode()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        modeJob?.cancel()
        modeJob = null
        widget = null
        super.onDetachedFromWindow()
    }

    private fun observeMode() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        modeJob?.cancel()
        modeJob = viewModel.isModeAllowed
            .onEach { allowed ->
                isModeAllowed = allowed
                updateVisibility()
            }
            .launchIn(scope)
    }

    private fun onClick() {
        val widget = widget ?: return
        val query = widget.text.trim().ifEmpty { null }
        widget.onOpenNewTabTapped?.invoke(query)
    }

    private fun updateVisibility() {
        val w = widget
        isVisible = isModeAllowed && w?.isDuckAiContext() == true && w.hasInputFocus()
    }

    private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ ->
        updateVisibility()
    }

    private fun findNativeInputWidget(): NativeInputWidget? {
        var node: View? = this
        while (node != null) {
            if (node is NativeInputWidget) return node
            node = node.parent as? View
        }
        return null
    }
}
