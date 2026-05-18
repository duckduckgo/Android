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
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.dataclearing.api.fire.FireDialog
import com.duckduckgo.dataclearing.api.fire.FireDialogProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentChatHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class ChatHistoryFragment : DuckDuckGoFragment(R.layout.fragment_chat_history) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var fireDialogProvider: FireDialogProvider

    private val binding: FragmentChatHistoryBinding by viewBinding()
    private val viewModel: ChatHistoryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[ChatHistoryViewModel::class.java]
    }

    private val adapter = ChatHistoryAdapter(
        onChatClicked = { item -> viewModel.onChatRowClicked(item.chatId) },
        onChatMoreClicked = { item, anchor -> showRowPopup(item, anchor) },
        onChatLongClicked = { item -> viewModel.onChatRowLongClicked(item.chatId) },
        onSelectAllClicked = { viewModel.onSelectAllToggled() },
    )

    private val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            when {
                binding.searchBar.isVisible -> hideSearchBar()
                viewModel.isSelectMode() -> viewModel.onSelectModeCancelled()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setTitle(R.string.duck_ai_chat_history_title)
        binding.toolbar.inflateMenu(R.menu.menu_chat_history_default)
        binding.toolbar.setOnMenuItemClickListener(::onMenuItemClicked)

        binding.chatHistoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.chatHistoryList.adapter = adapter

        binding.chatHistoryEmptyState.setOnPrimaryCtaClickListener { viewModel.onOpenDuckAiClicked() }

        binding.searchBar.onAction { action ->
            when (action) {
                is SearchBar.Action.PerformUpAction -> hideSearchBar()
                is SearchBar.Action.PerformSearch -> viewModel.onSearchQueryChanged(action.searchText)
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        childFragmentManager.setFragmentResultListener(FireDialog.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val event = bundle.getString(FireDialog.RESULT_KEY_EVENT)
            val confirmation = (viewModel.uiState.value as? ChatHistoryUiState.Loaded)?.confirmation
            when (event) {
                FireDialog.EVENT_ON_CLEAR_STARTED -> when (confirmation) {
                    is ChatHistoryUiState.PendingConfirmation.FireAll -> viewModel.onFireAllConfirmed()
                    is ChatHistoryUiState.PendingConfirmation.DeleteSelected -> viewModel.onDeleteSelectedConfirmed()
                    null -> Unit
                }
                FireDialog.EVENT_ON_CANCEL -> viewModel.onConfirmationCancelled()
            }
        }

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
                applyDefaultToolbar()
                setFireActionVisible(false)
            }
            ChatHistoryUiState.Empty -> {
                binding.chatHistoryList.visibility = View.GONE
                binding.chatHistoryEmptyState.visibility = View.VISIBLE
                adapter.submitList(emptyList())
                applyDefaultToolbar()
                setFireActionVisible(false)
            }
            is ChatHistoryUiState.Loaded -> {
                binding.chatHistoryList.visibility = View.VISIBLE
                binding.chatHistoryEmptyState.visibility = View.GONE
                val selectMode = state.mode as? ChatHistoryUiState.Mode.Selecting
                adapter.submitList(buildEntries(state, selectMode))
                if (selectMode != null) {
                    applySelectModeToolbar(selectMode.selectedChatIds.size)
                    setFireActionVisible(selectMode.selectedChatIds.isNotEmpty())
                } else {
                    applyDefaultToolbar()
                    // Hide fire when Recent is empty — title is "Delete N chats?" of the Recent count.
                    setFireActionVisible(state.recent.isNotEmpty())
                }
                renderConfirmation(state.confirmation)
            }
        }
        // Re-derive every render so a transition out of Loaded (e.g. last chat deleted externally)
        // can't leave us intercepting back presses with no overlay to dismiss.
        onBackPressedCallback.isEnabled = shouldInterceptBack(state)
    }

    private fun shouldInterceptBack(state: ChatHistoryUiState): Boolean {
        if (binding.searchBar.isVisible) return true
        val loaded = state as? ChatHistoryUiState.Loaded ?: return false
        return loaded.mode is ChatHistoryUiState.Mode.Selecting
    }

    private fun applyDefaultToolbar() {
        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.navigationContentDescription = null
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.setTitle(R.string.duck_ai_chat_history_title)
        binding.toolbar.menu.findItem(R.id.chat_history_action_search)?.isVisible = true
        binding.toolbar.menu.findItem(R.id.chat_history_action_overflow)?.isVisible = true
    }

    private fun applySelectModeToolbar(count: Int) {
        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.navigationContentDescription =
            getString(R.string.duck_ai_chat_history_exit_select_mode_content_description)
        binding.toolbar.setNavigationOnClickListener { viewModel.onSelectModeCancelled() }
        binding.toolbar.title = count.toString()
        binding.toolbar.menu.findItem(R.id.chat_history_action_search)?.isVisible = false
        binding.toolbar.menu.findItem(R.id.chat_history_action_overflow)?.isVisible = false
    }

    private fun buildEntries(
        state: ChatHistoryUiState.Loaded,
        selectMode: ChatHistoryUiState.Mode.Selecting?,
    ): List<ChatHistoryListEntry> = buildList {
        if (selectMode != null) {
            val visibleIds = (state.pinned + state.recent).map { it.chatId }.toSet()
            val allSelected = visibleIds.isNotEmpty() && selectMode.selectedChatIds == visibleIds
            add(ChatHistoryListEntry.SelectAllHeader(allSelected = allSelected))
        }
        if (state.pinned.isNotEmpty()) {
            if (!state.searchActive) add(ChatHistoryListEntry.Header(R.string.duck_ai_chat_history_section_pinned))
            state.pinned.forEach { item ->
                val selected = selectMode != null && item.chatId in selectMode.selectedChatIds
                add(ChatHistoryListEntry.Row(item = item, selected = selected))
            }
        }
        if (state.recent.isNotEmpty()) {
            if (!state.searchActive) add(ChatHistoryListEntry.Header(R.string.duck_ai_chat_history_section_recent))
            state.recent.forEach { item ->
                val selected = selectMode != null && item.chatId in selectMode.selectedChatIds
                add(ChatHistoryListEntry.Row(item = item, selected = selected))
            }
        }
    }

    private fun renderConfirmation(confirmation: ChatHistoryUiState.PendingConfirmation?) {
        if (confirmation == null) return
        if (childFragmentManager.findFragmentByTag(FIRE_DIALOG_TAG) != null) return

        val selectedChatUrls = viewModel.chatUrlsForDialog().orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val dialog = fireDialogProvider.createFireDialog(
                FireDialogProvider.FireDialogOrigin.ChatHistory(selectedChatUrls = selectedChatUrls),
            )
            if (childFragmentManager.findFragmentByTag(FIRE_DIALOG_TAG) != null) return@launch
            dialog.show(childFragmentManager, FIRE_DIALOG_TAG)
        }
    }

    private fun setFireActionVisible(visible: Boolean) {
        binding.toolbar.menu.findItem(R.id.chat_history_action_fire)?.isVisible = visible
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean = when (item.itemId) {
        R.id.chat_history_action_overflow -> {
            showToolbarOverflowPopup()
            true
        }
        R.id.chat_history_action_search -> {
            showSearchBar()
            true
        }
        R.id.chat_history_action_fire -> {
            viewModel.onFireIconClicked()
            true
        }
        else -> false
    }

    private fun showSearchBar() {
        onBackPressedCallback.isEnabled = true
        binding.toolbar.gone()
        binding.searchBar.handle(SearchBar.Event.ShowSearchBar)
        viewModel.onSearchActivated()
    }

    private fun hideSearchBar() {
        binding.searchBar.handle(SearchBar.Event.DismissSearchBar)
        requireActivity().hideKeyboard()
        binding.toolbar.show()
        viewModel.onSearchClosed()
        // onBackPressedCallback.isEnabled is reset by render() — select mode may still be active.
    }

    private fun showToolbarOverflowPopup() {
        val anchor = binding.toolbar.findViewById<View>(R.id.chat_history_action_overflow) ?: return
        val popup = PopupMenu(layoutInflater, R.layout.popup_chat_history_overflow)
        val view = popup.contentView
        popup.onMenuItemClicked(view.findViewById(R.id.select)) { viewModel.onEnterSelectMode() }
        popup.show(binding.root, anchor)
    }

    private fun showRowPopup(item: ChatHistoryItem, anchor: View) {
        val popup = PopupMenu(layoutInflater, R.layout.popup_chat_history_row)
        val view = popup.contentView
        popup.onMenuItemClicked(view.findViewById(R.id.pin)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.rename)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.download)) { showComingSoonSnackbar() }
        popup.onMenuItemClicked(view.findViewById(R.id.delete)) { viewModel.onDeleteSingleChat(item.chatId) }
        popup.show(binding.root, anchor)
    }

    private fun showComingSoonSnackbar() {
        Snackbar.make(binding.root, R.string.duck_ai_chat_history_coming_soon, Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        private const val FIRE_DIALOG_TAG = "chat_history_fire_dialog"

        fun newInstance(): ChatHistoryFragment = ChatHistoryFragment()
    }
}
