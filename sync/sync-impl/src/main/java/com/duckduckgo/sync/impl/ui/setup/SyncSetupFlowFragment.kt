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
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentSyncSetupBinding
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.AskSyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.RecoverSyncData
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.SyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.InitialSetupScreen
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.SyncAnotherDeviceScreen
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewState
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SyncSetupFlowFragment : DuckDuckGoFragment(R.layout.fragment_sync_setup) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentSyncSetupBinding by viewBinding()

    private var fragmentType: ViewMode = InitialSetupScreen

    private val viewModel: SyncSetupFlowViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncSetupFlowViewModel::class.java]
    }

    private val listener: SetupFlowListener?
        get() = activity as? SetupFlowListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.closeIcon.setOnClickListener {
            viewModel.onCloseClicked()
        }
    }

    private fun observeUiEvents() {
        viewModel
            .viewState(fragmentType)
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
            InitialSetupScreen -> {
                binding.contentIllustration.setImageResource(R.drawable.ic_sync_128)
                binding.contentTitle.text = getString(R.string.sync_enable_sync_title)
                binding.contentBody.text = getString(R.string.sync_enable_sync_content)
                binding.footerPrimaryButton.text = getString(R.string.sync_enable_sync_primary_button)
                binding.footerPrimaryButton.setOnClickListener {
                    viewModel.onTurnOnSyncClicked()
                }
                binding.footerSecondaryButton.text = getString(R.string.sync_enable_sync_secondary_button)
                binding.footerSecondaryButton.setOnClickListener {
                    viewModel.onRecoverYourSyncDataClicked()
                }
            }
            SyncAnotherDeviceScreen -> {
                binding.contentIllustration.setImageResource(R.drawable.ic_connect_device_128)
                binding.contentTitle.text = getString(R.string.sync_another_device_title)
                binding.contentBody.text = getString(R.string.sync_another_device_content)
                binding.footerPrimaryButton.text = getString(R.string.sync_another_device_primary_button)
                binding.footerPrimaryButton.setOnClickListener {
                    viewModel.onSyncAnotherDeviceClicked()
                }
                binding.footerSecondaryButton.text = getString(R.string.sync_another_device_secondary_button)
                binding.footerSecondaryButton.setOnClickListener {
                    viewModel.onNotNowClicked()
                }
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
            RecoverSyncData -> listener?.recoverYourSyncedData()
            AskSyncAnotherDevice -> listener?.askSyncAnotherDevice()
            SyncAnotherDevice -> listener?.syncAnotherDevice()
        }
    }

    companion object {
        fun instance(viewMode: ViewMode) = SyncSetupFlowFragment().apply {
            fragmentType = viewMode
        }
    }
}
