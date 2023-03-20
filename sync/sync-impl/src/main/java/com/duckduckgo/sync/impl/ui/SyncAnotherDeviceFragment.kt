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

package com.duckduckgo.sync.impl.ui

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
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.Command.LaunchSaveRecoveryCodeScreen
import com.duckduckgo.sync.impl.ui.SyncAnotherDeviceViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.*

@InjectWith(FragmentScope::class)
class SyncAnotherDeviceFragment : DuckDuckGoFragment(R.layout.fragment_sync_setup) {

    interface SyncAnotherDeviceListener {
        fun syncAnotherDevice()
        fun launchSaveRecoveryCodeScreen()
    }

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentSyncSetupBinding by viewBinding()

    private val viewModel: SyncAnotherDeviceViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncAnotherDeviceViewModel::class.java]
    }

    private val listener: SyncAnotherDeviceListener?
        get() = activity as? SyncAnotherDeviceListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.footerSecondaryButton.setOnClickListener {
            viewModel.onNotNowClicked()
        }
        binding.closeIcon.setOnClickListener {
            viewModel.onCloseClicked()
        }
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
        binding.contentIllustration.setImageResource(R.drawable.ic_connect_device_128)
        binding.contentTitle.text = getString(R.string.sync_another_device_title)
        binding.contentBody.text = getString(R.string.sync_another_device_content)
        binding.footerPrimaryButton.text = getString(R.string.sync_another_device_primary_button)
        binding.footerSecondaryButton.text = getString(R.string.sync_another_device_secondary_button)
    }

    private fun processCommand(it: Command) {
        when(it) {
            Finish -> requireActivity().finish()
            LaunchSaveRecoveryCodeScreen -> {
                listener?.launchSaveRecoveryCodeScreen()
            }
        }
    }

    companion object {
        fun instance() = SyncAnotherDeviceFragment()
    }
}
