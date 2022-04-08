/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doOnTextChanged
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewSearchBarBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

interface SearchBar {
    fun onAction(actionHandler: (Action) -> Unit)
    fun handle(event: Event)

    enum class Event {
        DismissSearchBar,
        ShowSearchBar
    }

    sealed class Action {
        object PerformUpAction : Action()
        data class PerformSearch(val searchText: String) : Action()
    }
}

class SearchBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.style.Widget_DuckDuckGo_SearchBarView
) : ConstraintLayout(context, attrs, defStyleAttr), SearchBar {
    private val binding: ViewSearchBarBinding by viewBinding()

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.SearchBarView,
            0,
            R.style.Widget_DuckDuckGo_SearchBarView
        ).apply {
            binding.root.background = getDrawable(R.styleable.SearchBarView_android_background)
            binding.omnibarTextInput.hint = getString(R.styleable.SearchBarView_searchHint)
            binding.clearTextButton.contentDescription = getString(R.styleable.SearchBarView_clearActionContentDescription)
            binding.upAction.contentDescription = getString(R.styleable.SearchBarView_upActionContentDescription)
            recycle()
        }
    }

    override fun onAction(actionHandler: (SearchBar.Action) -> Unit) {
        binding.upAction.setOnClickListener {
            actionHandler(SearchBar.Action.PerformUpAction)
        }
        binding.omnibarTextInput.doOnTextChanged { text, _, _, count ->
            binding.clearTextButton.visibility = if (count == 0) GONE else VISIBLE
            actionHandler(SearchBar.Action.PerformSearch(text.toString()))
        }
        binding.clearTextButton.setOnClickListener {
            clearText()
        }
    }

    override fun handle(event: SearchBar.Event) {
        when (event) {
            SearchBar.Event.DismissSearchBar -> {
                clearText()
                binding.root.visibility = GONE
            }
            SearchBar.Event.ShowSearchBar -> {
                clearText()
                binding.omnibarTextInput.showKeyboard()
                binding.root.visibility = VISIBLE
            }
        }
    }

    private fun clearText() {
        binding.omnibarTextInput.text?.clear()
    }
}
