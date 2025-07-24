/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui.tabs

import android.os.Build.VERSION
import android.os.Bundle
import android.view.View
import android.view.View.OVER_SCROLL_NEVER
import android.view.ViewTreeObserver
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchTabBinding
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.OmnibarPosition.TOP
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.BottomBlurView
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(FragmentScope::class)
class SearchTabFragment : DuckDuckGoFragment(R.layout.fragment_search_tab) {

    @Inject
    lateinit var newTabPagePlugins: ActivePluginPoint<NewTabPagePlugin>

    @Inject lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: InputScreenViewModel by lazy {
        ViewModelProvider(requireParentFragment(), viewModelFactory)[InputScreenViewModel::class.java]
    }

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private val binding: FragmentSearchTabBinding by viewBinding()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureNewTabPage()
        configureAutoComplete()
        configureObservers()
        configureBottomBlur()
    }

    private fun configureBottomBlur() {
        if (VERSION.SDK_INT >= 33) {
            // TODO: Handle overscroll when blurring
            binding.autoCompleteSuggestionsList.overScrollMode = OVER_SCROLL_NEVER

            val bottomBlurView = BottomBlurView(requireContext())
            bottomBlurView.setTargetView(binding.autoCompleteSuggestionsList)
            binding.bottomFadeContainer.addView(bottomBlurView)

            ViewTreeObserver.OnPreDrawListener {
                bottomBlurView.invalidate()
                true
            }.also { listener ->
                bottomBlurView.viewTreeObserver.addOnPreDrawListener(listener)
            }
        }
    }

    private fun configureNewTabPage() {
        // TODO: fix favorites click source to "focused state" instead of "new tab page"
        lifecycleScope.launch {
            newTabPagePlugins.getPlugins().firstOrNull()?.let { plugin ->
                val newTabPageView = plugin.getView(requireContext())
                binding.newTabContainerLayout.addView(newTabPageView)
            }
        }
    }

    private fun configureAutoComplete() {
        val context = context ?: return
        binding.autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(context)
        autoCompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                viewModel.userSelectedAutocomplete(it)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            },
            autoCompleteInAppMessageDismissedListener = {
                viewModel.onUserDismissedAutoCompleteInAppMessage()
            },
            autoCompleteOpenSettingsClickListener = {
                viewModel.onUserDismissedAutoCompleteInAppMessage()
                globalActivityStarter.start(context, PrivateSearchScreenNoParams)
            },
            autoCompleteLongPressClickListener = {
                viewModel.userLongPressedAutocomplete(it)
            },
            omnibarPosition = TOP,
        )
        binding.autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun configureObservers() {
        viewModel.visibilityState.onEach {
            binding.autoCompleteSuggestionsList.isVisible = it.autoCompleteSuggestionsVisible
            binding.bottomFadeContainer.isVisible = it.autoCompleteSuggestionsVisible

            if (!it.autoCompleteSuggestionsVisible) {
                viewModel.autoCompleteSuggestionsGone()
            }
        }.launchIn(lifecycleScope)

        viewModel.autoCompleteSuggestionResults.onEach {
            autoCompleteSuggestionsAdapter.updateData(it.query, it.suggestions)
        }.launchIn(lifecycleScope)
    }
}
