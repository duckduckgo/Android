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
import com.duckduckgo.duckchat.api.DuckChatContextual
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class StartChatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    @Inject lateinit var duckChatContextual: DuckChatContextual

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[StartChatViewModel::class.java]
    }

    private val icon: ImageView by lazy { findViewById(R.id.aiChatIconMenu) }
    private var visibilityJob: Job? = null

    var host: NativeInputHost? = null

    init {
        inflate(context, R.layout.view_start_chat, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        icon.setOnClickListener { onIconTapped() }
        observeVisibility()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        visibilityJob?.cancel()
        visibilityJob = null
    }

    private fun onIconTapped() {
        val host = host ?: return
        val tabId = host.tabId()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope
        val showMenu = viewModel.onIconClicked(inputEmpty = host.isInputEmpty()) == StartChatViewModel.IconAction.SHOW_MENU
        if (!showMenu || tabId == null || scope == null) {
            host.submit()
            return
        }
        // No page behind the new tab page, so the menu drops Ask About Page and offers New Chat and
        // Chats. Submitting stays the fallback for when the menu itself declines to show.
        scope.launch { duckChatContextual.launch(tabId, sourceUrl = null, anchor = icon) { host.submit() } }
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
}
