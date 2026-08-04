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

package com.duckduckgo.duckchat.impl.contextual.suggestions

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.doOnAttach
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ItemContextualSuggestionBinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ContextualSuggestionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel: ContextualSuggestionsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ContextualSuggestionsViewModel::class.java]
    }

    var onSuggestionSelected: ((ContextualSuggestedPrompt) -> Unit)? = null

    var onContentChanged: (() -> Unit)? = null

    private val loadingView = ContextualSuggestionsLoadingView(context)
    private val cardsContainer = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        addView(
            loadingView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = LOADING_MARGIN_BOTTOM_DP.toPx() },
        )
        addView(cardsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        loadingView.gone()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        viewModel.viewState
            .onEach { render(it) }
            .launchIn(scope)
    }

    fun load() {
        doOnAttach {
            viewModel.load()
        }
    }

    fun onPageContextUpdated(serializedPageContext: String) {
        doOnAttach {
            viewModel.onPageContextUpdated(serializedPageContext)
        }
    }

    fun clear() {
        doOnAttach {
            viewModel.clear()
        }
    }

    fun setReservedQuickActionSlots(count: Int) {
        doOnAttach {
            viewModel.onReservedQuickActionSlotsChanged(count)
        }
    }

    fun hasContent(): Boolean = loadingView.isVisible || cardsContainer.isNotEmpty()

    private fun render(viewState: ContextualSuggestionsViewModel.ViewState) {
        if (viewState.loading) loadingView.show() else loadingView.gone()

        cardsContainer.removeAllViews()
        if (!viewState.loading && viewState.suggestions.isNotEmpty()) {
            val inflater = LayoutInflater.from(context)
            viewState.suggestions.forEach { suggestion ->
                val itemBinding = ItemContextualSuggestionBinding.inflate(inflater, cardsContainer, false)
                itemBinding.suggestionLabel.text = suggestion.label
                itemBinding.suggestionLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(iconResFor(suggestion.icon), 0, 0, 0)
                itemBinding.root.setOnClickListener { onSuggestionSelected?.invoke(suggestion) }
                cardsContainer.addView(itemBinding.root)
            }
        }
        onContentChanged?.invoke()
    }

    @DrawableRes
    private fun iconResFor(icon: String?): Int = when (icon) {
        "summary" -> R.drawable.ic_summarize_16
        "translate" -> R.drawable.ic_translate_16
        else -> R.drawable.ic_idea_16
    }

    private companion object {
        const val LOADING_MARGIN_BOTTOM_DP = 8
    }
}
