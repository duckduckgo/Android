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

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentDeviceConnectedBinding
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.ViewState
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SyncDeviceConnectedFragment : DuckDuckGoFragment(R.layout.fragment_device_connected) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentDeviceConnectedBinding by viewBinding()

    private val viewModel: SyncDeviceConnectedViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncDeviceConnectedViewModel::class.java]
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
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            FinishSetupFlow -> listener?.launchFinishSetupFlow()
        }
    }

    private fun renderViewState(viewState: ViewState) {
        binding.connectedDeviceItem.setPrimaryText(viewState.deviceName)
        binding.connectedDeviceItem.setLeadingIconDrawable(ContextCompat.getDrawable(requireContext(), viewState.deviceType)!!)
        binding.footerPrimaryButton.setOnClickListener {
            viewModel.onNextClicked()
        }
    }

    companion object {
        fun instance() = SyncDeviceConnectedFragment()
    }
}
