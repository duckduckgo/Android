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
import com.duckduckgo.sync.impl.databinding.FragmentPreviousSessionReadyBinding
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.ContinueSetup
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.StartRestore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SyncPreviousSessionReadyFragment : DuckDuckGoFragment(R.layout.fragment_previous_session_ready) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentPreviousSessionReadyBinding by viewBinding()

    private val viewModel: SyncPreviousSessionReadyViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncPreviousSessionReadyViewModel::class.java]
    }

    private val listener: SyncSetupNavigationFlowListener?
        get() = activity as? SyncSetupNavigationFlowListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        configureListeners()
        observeUiEvents()
    }

    private fun configureListeners() {
        binding.closeIcon.setOnClickListener {
            viewModel.onCloseClicked()
        }
        binding.resumeButton.setOnClickListener {
            viewModel.onResumeClicked()
        }
        binding.continueSetupButton.setOnClickListener {
            viewModel.onContinueSetupClicked()
        }
    }

    private fun observeUiEvents() {
        viewModel
            .commands()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            StartRestore -> listener?.launchRestoreInProgressScreen()
            ContinueSetup -> listener?.launchContinueSetupSkippingRestoreCheck()
            Close -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
        }
    }

    companion object {
        fun instance() = SyncPreviousSessionReadyFragment()
    }
}
