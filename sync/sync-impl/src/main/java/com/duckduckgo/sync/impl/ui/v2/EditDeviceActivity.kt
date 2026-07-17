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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2EditDeviceBinding
import com.duckduckgo.sync.impl.ui.v2.EditDeviceContract.Companion.DEVICE_KEY
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class EditDeviceActivity : DuckDuckGoActivity() {
    private val device
        get() = requireNotNull(IntentCompat.getParcelableExtra(intent, DEVICE_KEY, ParcelableDevice::class.java)) {
            "Missing intent extra: '$DEVICE_KEY'"
        }.toConnectedDevice()

    private val binding by viewBinding<ActivitySyncV2EditDeviceBinding>()

    @Inject
    lateinit var vmFactory: Factory

    private val viewModel by viewModels<EditDeviceViewModel> {
        Provider(vmFactory, device)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        configureToolbar()
        configureRemoveAnotherDeviceItem()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.deviceHeader.setState(
            device = viewState.device,
        )

        binding.removeAnotherDeviceItem.isGone = viewState.device.thisDevice

        binding.editThisDeviceNameItem.isVisible = viewState.device.thisDevice
        binding.editThisDeviceNameItem.setSecondaryText(viewState.device.deviceName)
        binding.editThisDeviceNameDivider.isVisible = viewState.device.thisDevice
        binding.syncThisDeviceToggleContainer.isVisible = viewState.device.thisDevice

        binding.removeNoticeLabel.setText(if (viewState.device.thisDevice) R.string.sync_setup_v2_remove_this_device_notice else R.string.sync_setup_v2_remove_another_device_notice)
    }

    private fun processCommand(command: Command) {
        when (command) {
            Close -> finish()
        }
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            viewModel.onCloseClicked()
        }
    }

    private fun configureRemoveAnotherDeviceItem() {
        val color = ColorStateList.valueOf(getColorFromAttr(CommonR.attr.daxColorDestructive))
        binding.removeAnotherDeviceItem.apply {
            leadingIcon().imageTintList = color
            setPrimaryTextColorStateList(color)
        }
    }

    companion object {
        fun intent(
            context: Context,
            device: ParcelableDevice,
        ): Intent {
            return Intent(context, EditDeviceActivity::class.java).putExtra(DEVICE_KEY, device)
        }
    }
}
