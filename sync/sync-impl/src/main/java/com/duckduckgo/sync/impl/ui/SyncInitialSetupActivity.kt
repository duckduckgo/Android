/*
 * Copyright (c) 2022 DuckDuckGo
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
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.databinding.ActivitySyncSetupBinding
import com.duckduckgo.sync.impl.databinding.ItemConnectedDeviceBinding
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class)
class SyncInitialSetupActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncSetupBinding by viewBinding()
    private val viewModel: SyncInitialSetupViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        binding.createAccountButton.setOnClickListener { viewModel.onCreateAccountClicked() }
        binding.storeRecoveryCodeButton.setOnClickListener {
            viewModel.onStoreRecoveryCodeClicked()
        }
        binding.resetButton.setOnClickListener { viewModel.onResetClicked() }
        binding.loginAccountButton.setOnClickListener { viewModel.loginAccountClicked() }
        binding.logoutButton.setOnClickListener { viewModel.onLogoutClicked() }
        binding.deleteAccountButton.setOnClickListener { viewModel.onDeleteAccountClicked() }
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

    private fun processCommand(command: SyncInitialSetupViewModel.Command) {
        when (command) {
            is SyncInitialSetupViewModel.Command.ShowMessage -> {
                Toast.makeText(this, command.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderViewState(viewState: ViewState) {
        binding.uuidsViewGroup.isVisible = viewState.isSignedIn
        binding.accountStateTextView.isVisible = viewState.isSignedIn
        binding.userIdTextView.text = viewState.userId
        binding.deviceIdTextView.text = viewState.deviceId
        binding.tokenTextView.text = viewState.token
        binding.deviceNameTextView.text = viewState.deviceName
        binding.primaryKeyTextView.text = viewState.primaryKey
        binding.secretKeyTextView.text = viewState.secretKey
        binding.connectedDevicesList.removeAllViews()
        if (viewState.isSignedIn) {
            viewState.connectedDevices.forEach { device ->
                val connectedBinding = ItemConnectedDeviceBinding.inflate(layoutInflater, binding.connectedDevicesList, true)
                connectedBinding.deviceName.text = "${device.deviceName} ${if (device.thisDevice) "(This Device)" else ""}"
                connectedBinding.logoutButton.setOnClickListener {
                    viewModel.onLogoutClicked(device.deviceId)
                }
            }
        }
    }
}
