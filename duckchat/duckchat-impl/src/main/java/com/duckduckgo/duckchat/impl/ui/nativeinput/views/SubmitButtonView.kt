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
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ViewScope::class)
class SubmitButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SubmitButtonViewModel::class.java]
    }

    private val button: ImageView by lazy { findViewById(R.id.nativeInputSubmitButton) }
    private var stateJob: Job? = null

    var host: NativeInputHost? = null

    init {
        inflate(context, R.layout.view_native_input_submit_button, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        button.setOnClickListener { if (button.isEnabled) host?.onSubmitClicked() }
        observeState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
        stateJob = null
    }

    private fun observeState() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        stateJob?.cancel()
        stateJob = viewModel.viewState(editTabId())
            .onEach { state ->
                button.setImageResource(
                    if (state.duckAiIcon) R.drawable.ic_arrow_up_24 else CommonR.drawable.ic_arrow_right_24,
                )
                button.isEnabled = state.enabled
                button.alpha = if (state.enabled) ENABLED_ALPHA else DISABLED_ALPHA
                isVisible = state.visible
                (parent as? View)?.isVisible = state.visible
            }
            .launchIn(scope)
    }

    // On the edit surface the widget uses a session id the shared state flow never selects, so the
    // plugin reads that session's state directly. Elsewhere it follows the active tab.
    private fun editTabId(): String? = host?.takeIf { it.isEditSurface() }?.tabId()

    companion object {
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.4f
    }
}
