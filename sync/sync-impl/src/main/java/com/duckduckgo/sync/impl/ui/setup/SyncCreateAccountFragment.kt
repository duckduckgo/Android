/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentCreateAccountBinding
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.Error
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewMode.SignedIn
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.*

@InjectWith(FragmentScope::class)
class SyncCreateAccountFragment : DuckDuckGoFragment(R.layout.fragment_create_account) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentCreateAccountBinding by viewBinding()

    private val viewModel: SyncCreateAccountViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncCreateAccountViewModel::class.java]
    }

    private val listener: SetupFlowListener?
        get() = activity as? SetupFlowListener

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
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is SignedIn -> {
                listener?.launchFinishSetupFlow()
            }
            else -> {
                // nothing for now
            }
        }
    }

    private fun processCommand(it: Command) {
        when (it) {
            AbortFlow -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }

            FinishSetupFlow -> listener?.launchFinishSetupFlow()
            Error -> {
                Snackbar.make(binding.root, R.string.sync_general_error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun instance() = SyncCreateAccountFragment()
    }
}
