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

import android.os.Bundle
import android.transition.Transition
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchTabBinding
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.AutoCompleteViewState
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.OmnibarPosition.TOP
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.SuggestionItemDecoration
import com.duckduckgo.duckchat.impl.inputscreen.ui.util.renderIfChanged
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import javax.inject.Inject
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

    private lateinit var renderer: SearchInterstitialFragmentRenderer

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private val binding: FragmentSearchTabBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = SearchInterstitialFragmentRenderer()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.sharedElementEnterTransition?.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    setupNewTabPage()
                    transition.removeListener(this)
                }
                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )

        configureObservers()
        configureAutoComplete()
    }

    private fun setupNewTabPage() {
        lifecycleScope.launch {
            newTabPagePlugins.getPlugins().firstOrNull()?.let { plugin ->
                val newTabView = plugin.getView(requireContext())
                newTabView.alpha = 0f

                val displayMetrics = requireContext().resources.displayMetrics
                val slideDistance = displayMetrics.heightPixels * CONTENT_SLIDE_DISTANCE
                newTabView.translationY = -slideDistance

                binding.contentContainer.addView(
                    newTabView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                newTabView.animate()
                    .alpha(1f)
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()

                newTabView.animate()
                    .translationY(0f)
                    .setInterpolator(OvershootInterpolator(CONTENT_INTERPOLATOR_TENSION))
                    .setDuration(CONTENT_ANIMATION_DURATION)
                    .start()
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
        binding.autoCompleteSuggestionsList.addItemDecoration(
            SuggestionItemDecoration(
                divider = ContextCompat.getDrawable(context, R.drawable.suggestions_divider)!!,
                addExtraDividerPadding = true,
            ),
        )
    }

    inner class SearchInterstitialFragmentRenderer {

        private var lastSeenAutoCompleteViewState: AutoCompleteViewState? = null

        fun renderAutocomplete(viewState: AutoCompleteViewState) {
            renderIfChanged(viewState, lastSeenAutoCompleteViewState) {
                lastSeenAutoCompleteViewState = viewState

                if (viewState.showSuggestions || viewState.showFavorites) {
                    if (viewState.favorites.isNotEmpty() && viewState.showFavorites) {
                        if (binding.autoCompleteSuggestionsList.isVisible) {
                            viewModel.autoCompleteSuggestionsGone()
                        }
                        binding.autoCompleteSuggestionsList.gone()
                    } else {
                        binding.autoCompleteSuggestionsList.show()
                        autoCompleteSuggestionsAdapter.updateData(viewState.searchResults.query, viewState.searchResults.suggestions)
                    }
                } else {
                    if (binding.autoCompleteSuggestionsList.isVisible) {
                        viewModel.autoCompleteSuggestionsGone()
                    }
                    binding.autoCompleteSuggestionsList.gone()
                }
            }
        }
    }

    private fun configureObservers() {
        viewModel.autoCompleteViewState.observe(
            viewLifecycleOwner,
        ) {
            it?.let { renderer.renderAutocomplete(it) }
        }
    }

    companion object {
        private const val CONTENT_ANIMATION_DURATION = 500L
        private const val CONTENT_INTERPOLATOR_TENSION = 1F
        private const val CONTENT_SLIDE_DISTANCE = 0.05F
    }
}
