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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenFragment
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.BottomBlurView
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.RecyclerBottomSpacingDecoration
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.SwipeableRecyclerView
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
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
    private var bottomBlurView: BottomBlurView? = null
    private var bottomBlurLayoutListener: View.OnLayoutChangeListener? = null
    private var bottomBlurDataObserver: RecyclerView.AdapterDataObserver? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!duckChatFeature.aiChatSuggestions().isEnabled()) return
        configureChatSuggestions()
        configureObservers()
        configureBottomBlur()
    }

    private fun configureChatSuggestions() {
        val parentFragment = requireParentFragment() as InputScreenFragment

        chatSuggestionsAdapter = ChatSuggestionsAdapter { suggestion ->
            // TODO: Handle navigation to chat in duck.ai fullscreen mode
            logcat { "Chat suggestion clicked: chatId=${suggestion.chatId}, title=${suggestion.title}" }
        }

        chatSuggestionsRecyclerView = parentFragment.getChatSuggestionsRecyclerView().apply {
            setViewPager(parentFragment.getViewPager())
            adapter = chatSuggestionsAdapter
            layoutManager = LinearLayoutManager(context)
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.duckduckgo.mobile.android.R.attr.daxColorBrowserOverlay, typedValue, true)
            setBackgroundColor(typedValue.data)

            if (inputScreenConfigResolver.useTopBar()) {
                val spacing = resources.getDimensionPixelSize(R.dimen.inputScreenAutocompleteListBottomSpace)
                addItemDecoration(RecyclerBottomSpacingDecoration(spacing))
                updatePadding(top = 8f.toPx(context).roundToInt())
            }
        }
    }

    private fun configureObservers() {
        val parentFragment = requireParentFragment() as InputScreenFragment

        viewModel.chatSuggestions
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { suggestions ->
                chatSuggestionsAdapter.submitList(suggestions)
                if (!viewModel.visibilityState.value.searchMode) {
                    parentFragment.updateChatSuggestionsVisibility(suggestions.isNotEmpty())
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
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
            recyclerView.adapter?.registerAdapterDataObserver(bottomBlurDataObserver!!)
        }
    }

    override fun onDestroyView() {
        while ((chatSuggestionsRecyclerView?.itemDecorationCount ?: 0) > 0) {
            chatSuggestionsRecyclerView?.removeItemDecorationAt(0)
        }
        chatSuggestionsRecyclerView?.clearOnScrollListeners()
        bottomBlurLayoutListener?.let { listener ->
            chatSuggestionsRecyclerView?.removeOnLayoutChangeListener(listener)
        }
        bottomBlurLayoutListener = null
        bottomBlurDataObserver?.let { observer ->
            chatSuggestionsRecyclerView?.adapter?.unregisterAdapterDataObserver(observer)
        }
        bottomBlurDataObserver = null
        (bottomBlurView?.parent as? FrameLayout)?.removeView(bottomBlurView)
        bottomBlurView = null
        chatSuggestionsRecyclerView = null
        super.onDestroyView()
    }
}
