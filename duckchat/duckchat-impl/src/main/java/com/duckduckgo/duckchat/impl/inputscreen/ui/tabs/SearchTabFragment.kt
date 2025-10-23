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
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.ui.BrowserScreens.PrivateSearchScreenNoParams
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchTabBinding
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand.RestoreAutoCompleteScrollPosition
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand.ShowRemoveSearchSuggestionDialog
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.BottomBlurView
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.RecyclerBottomSpacingDecoration
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import com.duckduckgo.browser.ui.R as BrowserUI

@InjectWith(FragmentScope::class)
class SearchTabFragment : DuckDuckGoFragment(R.layout.fragment_search_tab) {
    @Inject
    lateinit var newTabPagePlugins: ActivePluginPoint<NewTabPagePlugin>

    @Inject lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var inputScreenConfigResolver: InputScreenConfigResolver

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
        if (VERSION.SDK_INT >= 33 && inputScreenConfigResolver.useTopBar()) {
            // TODO: Handle overscroll when blurring
            binding.autoCompleteSuggestionsList.overScrollMode = OVER_SCROLL_NEVER

            val bottomBlurView = BottomBlurView(requireContext())
            bottomBlurView.setTargetView(binding.autoCompleteSuggestionsList)
            binding.bottomFadeContainer.addView(bottomBlurView)

            ViewTreeObserver
                .OnPreDrawListener {
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
                val newTabPageView =
                    plugin.getView(requireContext(), showLogo = false) { hasContent ->
                        viewModel.onNewTabPageContentChanged(hasContent)
                        binding.newTabContainerLayout.isVisible = hasContent
                    }
                binding.newTabContainerLayout.addView(newTabPageView)
            }
        }
    }

    private fun configureAutoComplete() {
        val context = context ?: return
        binding.autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(context)
        if (inputScreenConfigResolver.useTopBar()) {
            val spacing = resources.getDimensionPixelSize(R.dimen.inputScreenAutocompleteListBottomSpace)
            val decoration = RecyclerBottomSpacingDecoration(spacing)
            binding.autoCompleteSuggestionsList.addItemDecoration(decoration)
            binding.autoCompleteSuggestionsList.updatePadding(top = 8f.toPx(context).roundToInt())
        }
        autoCompleteSuggestionsAdapter =
            BrowserAutoCompleteSuggestionsAdapter(
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
                omnibarType =
                if (inputScreenConfigResolver.useTopBar()) {
                    OmnibarType.SINGLE_TOP
                } else {
                    OmnibarType.SINGLE_BOTTOM
                },
            )
        binding.autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun configureObservers() {
        viewModel.visibilityState
            .onEach {
                binding.autoCompleteSuggestionsList.isVisible = it.autoCompleteSuggestionsVisible
                binding.bottomFadeContainer.isVisible = it.bottomFadeVisible

                if (!it.autoCompleteSuggestionsVisible) {
                    viewModel.autoCompleteSuggestionsGone()
                }
            }.launchIn(lifecycleScope)

        viewModel.autoCompleteSuggestionResults
            .onEach {
                autoCompleteSuggestionsAdapter.updateData(it.query, it.suggestions)
            }.launchIn(lifecycleScope)

        viewModel.searchTabCommand.observe(viewLifecycleOwner) {
            processCommand(it)
        }
    }

    private fun processCommand(command: SearchCommand) {
        when (command) {
            is ShowRemoveSearchSuggestionDialog -> showRemoveSearchSuggestionDialog(command.suggestion)
            is RestoreAutoCompleteScrollPosition ->
                restoreAutoCompleteScrollPosition(
                    command.firstVisibleItemPosition,
                    command.itemOffsetTop,
                )
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        storeAutocompletePosition()

        TextAlertDialogBuilder(requireContext())
            .setTitle(BrowserUI.string.autocompleteRemoveItemTitle)
            .setCancellable(true)
            .setPositiveButton(BrowserUI.string.autocompleteRemoveItemRemove)
            .setNegativeButton(BrowserUI.string.autocompleteRemoveItemCancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onRemoveSearchSuggestionConfirmed(suggestion)
                        viewModel.restoreAutoCompleteScrollPosition()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.restoreAutoCompleteScrollPosition()
                    }

                    override fun onDialogCancelled() {
                        viewModel.restoreAutoCompleteScrollPosition()
                    }
                },
            ).show()
    }

    private fun storeAutocompletePosition() {
        val layoutManager = binding.autoCompleteSuggestionsList.layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val itemOffsetTop = layoutManager.findViewByPosition(firstVisibleItemPosition)?.top ?: 0
        viewModel.storeAutoCompleteScrollPosition(firstVisibleItemPosition, itemOffsetTop)
    }

    private fun restoreAutoCompleteScrollPosition(
        position: Int,
        offset: Int,
    ) {
        val layoutListener =
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.autoCompleteSuggestionsList.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    scrollToPositionWithOffset(position, offset)
                }
            }
        binding.autoCompleteSuggestionsList.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    private fun scrollToPositionWithOffset(
        position: Int,
        offset: Int,
    ) {
        val layoutManager = binding.autoCompleteSuggestionsList.layoutManager as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(position, offset)
    }
}
