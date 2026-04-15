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

package com.duckduckgo.duckchat.impl.inputscreen.ui.tabs

import android.os.Build.VERSION
import android.os.Bundle
import android.view.View
import android.view.View.OVER_SCROLL_NEVER
import android.widget.FrameLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenFragment
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSearchSuggestionAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.SectionDividerAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.BottomBlurView
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.SwipeableRecyclerView
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.roundToInt

@InjectWith(FragmentScope::class)
class ChatTabFragment : DuckDuckGoFragment(R.layout.fragment_chat_tab) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var inputScreenConfigResolver: InputScreenConfigResolver

    @Inject
    lateinit var duckChatFeature: DuckChatFeature

    private val viewModel: InputScreenViewModel by lazy {
        ViewModelProvider(requireParentFragment(), viewModelFactory)[InputScreenViewModel::class.java]
    }

    private var chatSuggestionsRecyclerView: SwipeableRecyclerView? = null
    private lateinit var chatSuggestionsAdapter: ChatSuggestionsAdapter
    private var chatUrlSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter? = null
    private var chatSearchSuggestionAdapter: ChatSearchSuggestionAdapter? = null
    private var chatUrlDividerAdapter: SectionDividerAdapter? = null
    private var searchDividerAdapter: SectionDividerAdapter? = null
    private var concatAdapter: ConcatAdapter? = null
    private var bottomBlurView: BottomBlurView? = null
    private var bottomBlurLayoutListener: View.OnLayoutChangeListener? = null
    private var bottomBlurDataObserver: RecyclerView.AdapterDataObserver? = null
    private var bottomBlurObserverAdapter: RecyclerView.Adapter<*>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!duckChatFeature.aiChatSuggestions().isEnabled()) return
        configureChatSuggestions()
        if (duckChatFeature.rememberTogglePosition().isEnabled()) {
            configureChatUrlSuggestions()
        }
        configureObservers()
        configureBottomBlur()
    }

    private fun configureChatSuggestions() {
        val parentFragment = requireParentFragment() as InputScreenFragment

        chatSuggestionsAdapter = ChatSuggestionsAdapter { suggestion ->
            viewModel.onChatSuggestionSelected(suggestion.chatId, suggestion.pinned)
        }

        chatSuggestionsRecyclerView = parentFragment.getChatSuggestionsRecyclerView().apply {
            setViewPager(parentFragment.getViewPager())
            adapter = chatSuggestionsAdapter
            layoutManager = LinearLayoutManager(context)
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.duckduckgo.mobile.android.R.attr.daxColorBrowserOverlay, typedValue, true)
            setBackgroundColor(typedValue.data)

            if (inputScreenConfigResolver.useTopBar()) {
                val bottomSpacing = resources.getDimensionPixelSize(R.dimen.inputScreenAutocompleteListBottomSpace)
                updatePadding(top = 8f.toPx(context).roundToInt(), bottom = bottomSpacing)
            }
        }
        chatSuggestionsRecyclerView?.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        parentFragment.dismissKeyboard()
                    }
                }
            },
        )
    }

    private fun configureObservers() {
        val parentFragment = requireParentFragment() as InputScreenFragment

        if (!duckChatFeature.rememberTogglePosition().isEnabled()) {
            configureLegacyObservers(parentFragment)
            return
        }

        combine(
            viewModel.chatSuggestions,
            viewModel.chatUrlSuggestions,
            viewModel.chatInputText,
        ) { chatSuggestions, urlSuggestions, chatInput ->
            Triple(chatSuggestions, urlSuggestions, chatInput)
        }.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { (chatSuggestions, urlSuggestions, chatInput) ->
                if (viewModel.visibilityState.value.searchMode) return@onEach
                val isTyping = chatInput.isNotEmpty()
                val hasChatSuggestions = chatSuggestions.isNotEmpty()
                val hasUrlSuggestions = urlSuggestions.suggestions.isNotEmpty()

                chatSuggestionsAdapter.submitList(chatSuggestions)

                val showUrlSuggestions = isTyping && hasUrlSuggestions
                if (showUrlSuggestions) {
                    chatUrlSuggestionsAdapter?.updateData(urlSuggestions.query, urlSuggestions.suggestions)
                } else {
                    chatUrlSuggestionsAdapter?.updateData("", emptyList())
                }

                chatSearchSuggestionAdapter?.update(chatInput, visible = isTyping)

                // Dividers show between non-empty adjacent sections
                chatUrlDividerAdapter?.setVisible(hasChatSuggestions && showUrlSuggestions)
                searchDividerAdapter?.setVisible((hasChatSuggestions || showUrlSuggestions) && isTyping)

                val hasAnySuggestions = hasChatSuggestions || isTyping
                parentFragment.updateChatSuggestionsVisibility(hasAnySuggestions)
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun configureLegacyObservers(parentFragment: InputScreenFragment) {
        viewModel.chatSuggestions
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { suggestions ->
                chatSuggestionsAdapter.submitList(suggestions)
                if (!viewModel.visibilityState.value.searchMode) {
                    parentFragment.updateChatSuggestionsVisibility(suggestions.isNotEmpty())
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun configureChatUrlSuggestions() {
        chatUrlSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = { viewModel.userSelectedAutocomplete(it, fromChatUrlSuggestions = true) },
            editableSearchClickListener = { },
            autoCompleteLongPressClickListener = { },
            omnibarType = if (inputScreenConfigResolver.useTopBar()) OmnibarType.SINGLE_TOP else OmnibarType.SINGLE_BOTTOM,
            hideEditQueryArrow = true,
            hideSectionDividers = true,
        )
        chatSearchSuggestionAdapter = ChatSearchSuggestionAdapter { query ->
            viewModel.onSearchSubmitted(query)
        }
        chatUrlDividerAdapter = SectionDividerAdapter()
        searchDividerAdapter = SectionDividerAdapter()
        concatAdapter = ConcatAdapter(
            chatSuggestionsAdapter,
            chatUrlDividerAdapter,
            chatUrlSuggestionsAdapter,
            searchDividerAdapter,
            chatSearchSuggestionAdapter,
        )
        chatSuggestionsRecyclerView?.adapter = concatAdapter
    }

    private fun configureBottomBlur() {
        if (VERSION.SDK_INT >= 33 && inputScreenConfigResolver.useTopBar()) {
            val recyclerView = chatSuggestionsRecyclerView ?: return
            val parentFragment = requireParentFragment() as InputScreenFragment
            val bottomFadeContainer = parentFragment.getChatSuggestionsBottomFadeContainer()

            recyclerView.overScrollMode = OVER_SCROLL_NEVER

            bottomBlurView = BottomBlurView(requireContext())
            bottomBlurView?.setTargetView(recyclerView)
            bottomFadeContainer.addView(bottomBlurView)

            recyclerView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        bottomBlurView?.invalidate()
                    }
                },
            )

            bottomBlurLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                bottomBlurView?.invalidate()
            }
            recyclerView.addOnLayoutChangeListener(bottomBlurLayoutListener)

            bottomBlurDataObserver = object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    recyclerView.post { bottomBlurView?.invalidate() }
                }
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    recyclerView.post { bottomBlurView?.invalidate() }
                }
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    recyclerView.post { bottomBlurView?.invalidate() }
                }
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    recyclerView.post { bottomBlurView?.invalidate() }
                }
            }
            bottomBlurObserverAdapter = recyclerView.adapter
            bottomBlurObserverAdapter?.registerAdapterDataObserver(bottomBlurDataObserver!!)
        }
    }

    override fun onDestroyView() {
        chatSuggestionsRecyclerView?.clearOnScrollListeners()
        bottomBlurLayoutListener?.let { listener ->
            chatSuggestionsRecyclerView?.removeOnLayoutChangeListener(listener)
        }
        bottomBlurLayoutListener = null
        bottomBlurDataObserver?.let { observer ->
            bottomBlurObserverAdapter?.unregisterAdapterDataObserver(observer)
        }
        bottomBlurObserverAdapter = null
        bottomBlurDataObserver = null
        (bottomBlurView?.parent as? FrameLayout)?.removeView(bottomBlurView)
        bottomBlurView = null
        chatSuggestionsRecyclerView = null
        super.onDestroyView()
    }
}
