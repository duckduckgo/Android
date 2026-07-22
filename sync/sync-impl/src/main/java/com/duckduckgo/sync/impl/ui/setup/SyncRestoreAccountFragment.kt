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

package com.duckduckgo.sync.impl.ui.setup

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.RestorationComplete
import com.duckduckgo.sync.impl.ui.setup.SyncRestoreAccountViewModel.Command.ShowError
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SyncRestoreAccountFragment : DuckDuckGoFragment(R.layout.fragment_create_account) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel: SyncRestoreAccountViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncRestoreAccountViewModel::class.java]
    }

    private val listener: SyncSetupNavigationFlowListener?
        get() = activity as? SyncSetupNavigationFlowListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.CREATED)
            .launchIn(viewLifecycleOwner.lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            RestorationComplete -> listener?.launchRecoveryCodeScreen()
            AbortFlow -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
            is ShowError -> showError(it)
        }
    }

    private fun showError(it: ShowError) {
        val context = context ?: return
        TextAlertDialogBuilder(context)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(it.message) + if (it.reason.isNotEmpty()) "\n" + it.reason else "")
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            ).show()
    }

    companion object {
        fun instance(): Fragment = SyncRestoreAccountFragment()
    }
}
