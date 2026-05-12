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

package com.duckduckgo.duckchat.impl.history

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentChatHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ChatHistoryFragment : DuckDuckGoFragment(R.layout.fragment_chat_history) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var duckChat: DuckChatInternal

    @Inject
    lateinit var pixel: Pixel

    private val binding: FragmentChatHistoryBinding by viewBinding()
    private val viewModel: ChatHistoryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[ChatHistoryViewModel::class.java]
    }

    private val adapter = ChatHistoryAdapter(
        onChatClicked = { item -> duckChat.openWithChatId(item.chatId) },
        onChatMoreClicked = { _, anchor -> showRowPopup(anchor) },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setTitle(R.string.duck_ai_chat_history_title)
        binding.toolbar.inflateMenu(R.menu.menu_chat_history_default)
        binding.toolbar.setOnMenuItemClickListener(::onMenuItemClicked)

        binding.chatHistoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatHistoryList.adapter = adapter

        binding.chatHistoryEmptyState.setOnPrimaryCtaClickListener { duckChat.openDuckChat() }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: ChatHistoryUiState) {
        logcat { "ChatHistory: render ${state::class.simpleName}" }
        when (state) {
            ChatHistoryUiState.Loading -> {
                binding.chatHistoryList.visibility = View.GONE
                binding.chatHistoryEmptyState.visibility = View.GONE
            }
            ChatHistoryUiState.Empty -> {
                binding.chatHistoryList.visibility = View.GONE
                binding.chatHistoryEmptyState.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            }
            is ChatHistoryUiState.Loaded -> {
                binding.chatHistoryList.visibility = View.VISIBLE
                binding.chatHistoryEmptyState.visibility = View.GONE
                adapter.submitList(buildEntries(state))
            }
        }
    }

    private fun buildEntries(state: ChatHistoryUiState.Loaded): List<ChatHistoryListEntry> = buildList {
        if (state.pinned.isNotEmpty()) {
            add(ChatHistoryListEntry.Header(R.string.duck_ai_chat_history_section_pinned))
            state.pinned.forEach { add(ChatHistoryListEntry.Row(it)) }
        }
        if (state.recent.isNotEmpty()) {
            add(ChatHistoryListEntry.Header(R.string.duck_ai_chat_history_section_recent))
            state.recent.forEach { add(ChatHistoryListEntry.Row(it)) }
        }
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean = when (item.itemId) {
        R.id.chat_history_action_overflow -> {
            showToolbarOverflowPopup()
            true
        }
        R.id.chat_history_action_fire,
        R.id.chat_history_action_search,
        -> {
            showComingSoonSnackbar()
            true
        }
        else -> false
    }

    private fun showToolbarOverflowPopup() {
        val anchor = binding.toolbar.findViewById<View>(R.id.chat_history_action_overflow) ?: return
        val popup = PopupMenu(layoutInflater, R.layout.popup_chat_history_overflow)
        val view = popup.contentView
        popup.onMenuItemClicked(view.findViewById(R.id.select)) { showComingSoonSnackbar() }
        popup.show(binding.root, anchor)
    }

    private fun showRowPopup(anchor: View) {
        val popup = PopupMenu(layoutInflater, R.layout.popup_chat_history_row)
        val view = popup.contentView
        popup.onMenuItemClicked(view.findViewById(R.id.pin)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.rename)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.download)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.delete)) { showComingSoonSnackbar() }
        popup.show(binding.root, anchor)
    }

    private fun showComingSoonSnackbar() {
        Snackbar.make(binding.root, R.string.duck_ai_chat_history_coming_soon, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(): ChatHistoryFragment = ChatHistoryFragment()
    }
}
