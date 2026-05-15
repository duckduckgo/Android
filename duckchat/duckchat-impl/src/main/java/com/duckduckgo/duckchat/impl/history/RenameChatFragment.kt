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
import android.text.Editable
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentRenameChatBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class RenameChatFragment : DuckDuckGoFragment(R.layout.fragment_rename_chat) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentRenameChatBinding by viewBinding()
    private val viewModel: RenameChatViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[RenameChatViewModel::class.java]
    }

    private val chatId: String by lazy { checkNotNull(requireArguments().getString(ARG_CHAT_ID)) }
    private val initialTitle: String by lazy { requireArguments().getString(ARG_CURRENT_TITLE).orEmpty() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setTitle(R.string.duck_ai_chat_history_rename_title)
        binding.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_arrow_left_24)
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.toolbar.inflateMenu(R.menu.menu_rename_chat)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_rename_confirm) {
                onConfirmClicked()
                true
            } else {
                false
            }
        }

        binding.titleInput.text = initialTitle
        binding.titleInput.addTextChangedListener(titleTextWatcher)
        binding.titleInput.showKeyboardDelayed()
        setConfirmEnabled(false)

        viewModel.results
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach(::onRenameResult)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun onConfirmClicked() {
        viewModel.onSaveClicked(chatId, binding.titleInput.text)
    }

    private fun onRenameResult(result: RenameChatViewModel.RenameResult) {
        when (result) {
            RenameChatViewModel.RenameResult.Success -> dismiss()
            is RenameChatViewModel.RenameResult.Error ->
                Snackbar.make(binding.root, R.string.duck_ai_chat_history_rename_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun dismiss() {
        requireActivity().hideKeyboard()
        parentFragmentManager.popBackStack()
    }

    private fun setConfirmEnabled(enabled: Boolean) {
        binding.toolbar.menu.findItem(R.id.action_rename_confirm)?.isEnabled = enabled
    }

    private val titleTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            val candidate = editable.toString().trim()
            setConfirmEnabled(candidate.isNotBlank() && candidate != initialTitle.trim())
        }
    }

    companion object {
        private const val ARG_CHAT_ID = "arg_chat_id"
        private const val ARG_CURRENT_TITLE = "arg_current_title"

        fun newInstance(chatId: String, currentTitle: String): RenameChatFragment = RenameChatFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CHAT_ID, chatId)
                putString(ARG_CURRENT_TITLE, currentTitle)
            }
        }
    }
}
