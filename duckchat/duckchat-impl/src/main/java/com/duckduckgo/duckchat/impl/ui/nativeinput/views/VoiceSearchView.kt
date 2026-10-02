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

@InjectWith(ViewScope::class)
class VoiceSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[VoiceSearchViewModel::class.java]
    }

    private val button: ImageView by lazy { findViewById(R.id.nativeInputVoiceSearchButton) }
    private var visibilityJob: Job? = null

    var host: NativeInputHost? = null
    var isEditMode: Boolean = false

    init {
        inflate(context, R.layout.view_native_input_voice_search_button, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        button.setOnClickListener { host?.onVoiceSearchClicked() }
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
        visibilityJob = viewModel.availableWhenBlank(host?.takeIf { it.isEditSurface() }?.tabId())
            .onEach { availableWhenBlank ->
                val show = shouldShowVoiceSearch(availableWhenBlank, isEditMode)
                isVisible = show
                (parent as? View)?.isVisible = show
            }
            .launchIn(scope)
    }
}

// Voice search is offered only when available and the field is blank, and never on the edit surface.
internal fun shouldShowVoiceSearch(
    availableWhenBlank: Boolean,
    isEditMode: Boolean,
): Boolean = availableWhenBlank && !isEditMode
