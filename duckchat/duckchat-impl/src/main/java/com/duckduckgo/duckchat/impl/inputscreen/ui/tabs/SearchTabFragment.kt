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
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchTabBinding
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.OmnibarPosition.TOP
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.SuggestionItemDecoration
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.api.views.FavoritesGridConfig
import com.duckduckgo.savedsites.api.views.FavoritesPlacement
import com.duckduckgo.savedsites.api.views.SavedSitesViewsProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SearchTabFragment : DuckDuckGoFragment(R.layout.fragment_search_tab) {

    @Inject
    lateinit var savedSitesViewsProvider: SavedSitesViewsProvider

    @Inject lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: InputScreenViewModel by lazy {
        ViewModelProvider(requireParentFragment(), viewModelFactory)[InputScreenViewModel::class.java]
    }

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private val binding: FragmentSearchTabBinding by viewBinding()
    private lateinit var favoritesView: View

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.sharedElementEnterTransition?.addListener(
            object : Transition.TransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    configureFavorites()
                    configureAutoComplete()
                    configureObservers()
                    transition.removeListener(this)
                }

                override fun onTransitionStart(transition: Transition) {}
                override fun onTransitionCancel(transition: Transition) {}
                override fun onTransitionPause(transition: Transition) {}
                override fun onTransitionResume(transition: Transition) {}
            },
        )
    }

    private fun configureFavorites() {
        val favoritesGridConfig = FavoritesGridConfig(
            isExpandable = false,
            showPlaceholders = false,
            placement = FavoritesPlacement.FOCUSED_STATE,
        )
        favoritesView = savedSitesViewsProvider.getFavoritesGridView(requireContext(), config = favoritesGridConfig)
        favoritesView.alpha = 0f

        val displayMetrics = requireContext().resources.displayMetrics
        val slideDistance = displayMetrics.heightPixels * CONTENT_SLIDE_DISTANCE
        favoritesView.translationY = -slideDistance

        binding.contentContainer.addView(favoritesView)

        favoritesView.animate()
            .alpha(1f)
            .setDuration(CONTENT_ANIMATION_DURATION)
            .start()

        favoritesView.animate()
            .translationY(0f)
            .setInterpolator(OvershootInterpolator(CONTENT_INTERPOLATOR_TENSION))
            .setDuration(CONTENT_ANIMATION_DURATION)
            .start()
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

    private fun configureObservers() {
        viewModel.visibilityState.onEach {
            binding.autoCompleteSuggestionsList.isVisible = it.autoCompleteSuggestionsVisible
            if (!it.autoCompleteSuggestionsVisible) {
                viewModel.autoCompleteSuggestionsGone()
            }
        }.launchIn(lifecycleScope)

        viewModel.autoCompleteSuggestionResults.onEach {
            autoCompleteSuggestionsAdapter.updateData(it.query, it.suggestions)
        }.launchIn(lifecycleScope)
    }

    companion object {
        private const val CONTENT_ANIMATION_DURATION = 500L
        private const val CONTENT_INTERPOLATOR_TENSION = 1F
        private const val CONTENT_SLIDE_DISTANCE = 0.05F
    }
}
