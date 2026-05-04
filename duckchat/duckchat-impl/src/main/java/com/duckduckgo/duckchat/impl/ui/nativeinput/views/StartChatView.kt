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
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
class StartChatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[StartChatViewModel::class.java]
    }

    private val icon: ImageView by lazy { findViewById(R.id.aiChatIconMenu) }
    private var visibilityJob: Job? = null

    init {
        inflate(context, R.layout.view_start_chat, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        icon.setOnClickListener {
            val submitted = findNativeInputWidget()?.submitAsChat() == true
            if (!submitted) viewModel.openNewChat()
        }
        observeVisibility()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        visibilityJob?.cancel()
        visibilityJob = null
    }

    private fun observeVisibility() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        visibilityJob?.cancel()
        visibilityJob = viewModel.isVisible
            .onEach { visible ->
                isVisible = visible
                (parent as? View)?.isVisible = visible
            }
            .launchIn(scope)
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
