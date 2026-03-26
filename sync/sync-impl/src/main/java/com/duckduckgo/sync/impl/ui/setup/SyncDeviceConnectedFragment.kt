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
import com.duckduckgo.sync.impl.databinding.FragmentDeviceConnectedBinding
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.*

@InjectWith(FragmentScope::class)
class SyncDeviceConnectedFragment : DuckDuckGoFragment(R.layout.fragment_device_connected) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentDeviceConnectedBinding by viewBinding()

    private val viewModel: SyncDeviceConnectedViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncDeviceConnectedViewModel::class.java]
    }

    private val listener: SyncSetupFlowFinishedListener?
        get() = activity as? SyncSetupFlowFinishedListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
    }

    private fun observeUiEvents() {
        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        binding.footerPrimaryButton.setOnClickListener {
            viewModel.onDoneClicked()
        }

        binding.footerSecondaryButton.setOnClickListener {
            viewModel.onGetAppOnOtherDevicesClicked()
        }
    }

    private fun processCommand(it: Command) {
        when (it) {
            FinishSetupFlow -> listener?.finishSetup()
            Command.Error -> {
                Snackbar.make(binding.root, R.string.sync_general_error, Snackbar.LENGTH_LONG).show()
            }
            LaunchSyncGetOnOtherPlatforms -> listener?.launchGetAppOnOtherPlatformsScreen()
        }
    }

    companion object {
        fun instance() = SyncDeviceConnectedFragment()
    }
}
