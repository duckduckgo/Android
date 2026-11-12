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

package com.duckduckgo.sync.internal.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.internal.databinding.ActivitySyncAuditLogDebugBinding
import com.duckduckgo.sync.internal.databinding.ItemOtherSyncedDeviceBinding
import com.duckduckgo.sync.internal.ui.SyncAuditLogDebugViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class SyncAuditLogDebugActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivitySyncAuditLogDebugBinding by viewBinding()
    private val viewModel: SyncAuditLogDebugViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)
        configureListeners()
        observeViewState()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }

    private fun configureListeners() {
        binding.readServerLogButton.setOnClickListener { viewModel.onReadServerLogClicked() }
        binding.findUnseenAuditEntriesButton.setOnClickListener { viewModel.onFindUnseenAuditEntriesClicked() }
        binding.markAllAuditLoginsSeenButton.setOnClickListener { viewModel.onMarkAllAuditLoginsSeenClicked() }
        binding.simulateLoginButton.setOnClickListener { viewModel.onSimulateLoginClicked() }
    }

    private fun observeViewState() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(state: ViewState) {
        binding.loginAuditLogToggle.quietlySetIsChecked(state.loginAuditLogEnabled) { _, enabled ->
            viewModel.onLoginAuditLogFlagChanged(enabled)
        }
        binding.loginNotificationToggle.quietlySetIsChecked(state.loginNotificationEnabled) { _, enabled ->
            viewModel.onLoginNotificationFlagChanged(enabled)
        }
        binding.serverLogResultTextView.text = state.serverLogResult
        binding.unseenLoginsCountTextView.text = state.unseenLoginsCount
        binding.unseenLoginsResultTextView.text = state.unseenLoginsResult
        binding.otherDevicesCountTextView.text = state.otherDevicesListError ?: "Other synced devices: ${state.otherDevices.size}"
        binding.otherDevicesResultTextView.text = state.otherDevicesResult
        renderOtherDevices(state.otherDevices)
    }

    private fun renderOtherDevices(devices: List<ConnectedDevice>) {
        binding.otherDevicesContainer.removeAllViews()
        devices.forEach { device ->
            val row = ItemOtherSyncedDeviceBinding.inflate(layoutInflater, binding.otherDevicesContainer, true)
            row.otherDeviceName.text = device.deviceName
            row.otherDeviceId.text = device.deviceId
            row.logOutOtherDeviceButton.setOnClickListener { viewModel.onLogOutOtherDeviceClicked(device) }
        }
    }
}
