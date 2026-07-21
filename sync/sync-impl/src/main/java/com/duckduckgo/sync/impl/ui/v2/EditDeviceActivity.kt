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
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2EditDeviceBinding
import com.duckduckgo.sync.impl.databinding.DialogEditDeviceBinding
import com.duckduckgo.sync.impl.ui.v2.EditDeviceContract.Companion.DEVICE_KEY
import com.duckduckgo.sync.impl.ui.v2.EditDeviceContract.Companion.RESULT_DEVICE_EDITED
import com.duckduckgo.sync.impl.ui.v2.EditDeviceContract.Companion.RESULT_DEVICE_REMOVED
import com.duckduckgo.sync.impl.ui.v2.EditDeviceContract.Companion.RESULT_SYNC_TURNED_OFF
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.ResetTurnOffSyncToggle
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.SetDeviceEditedResult
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.SetDeviceRemovedResult
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.SetSyncTurnedOffResult
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.v2.EditDeviceViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class EditDeviceActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2EditDeviceBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val viewModel by viewModels<EditDeviceViewModel> {
        val device = requireNotNull(IntentCompat.getParcelableExtra(intent, DEVICE_KEY, ParcelableDevice::class.java)) {
            "Missing intent extra: '$DEVICE_KEY'"
        }

        Provider(vmFactory, device.toConnectedDevice())
    }

    private val currentDevice get() = viewModel.viewState.value.device

    private val turnOffSyncListener = OnCheckedChangeListener { _, isChecked ->
        if (!isChecked) viewModel.onTurnOffSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEdgeToEdge = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (isEdgeToEdge) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (isEdgeToEdge) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureEditDeviceNameItem()
        configureRemoveAnotherDeviceItem()
        configureTurnOffSyncToggle()

        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
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

        binding.editThisDeviceNameItem.isVisible = viewState.device.thisDevice && !viewState.isEditingName
        binding.editThisDeviceNameItem.setSecondaryText(viewState.device.deviceName)
        binding.editThisDeviceNameShimmer.apply {
            val showShimmer = viewState.device.thisDevice && viewState.isEditingName
            isVisible = showShimmer
            if (showShimmer) startShimmer() else stopShimmer()
        }
        binding.editThisDeviceNameDivider.isVisible = viewState.device.thisDevice
        binding.syncThisDeviceToggleContainer.isVisible = viewState.device.thisDevice

        binding.removeNoticeLabel.setText(
            if (viewState.device.thisDevice) {
                R.string.sync_setup_v2_remove_this_device_notice
            } else {
                R.string.sync_setup_v2_remove_another_device_notice
            },
        )
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AskEditDevice -> {
                askEditDevice()
            }

            is SetDeviceEditedResult -> {
                setResult(RESULT_DEVICE_EDITED, EditDeviceContract.resultIntent(currentDevice))
            }

            is AskRemoveDevice -> {
                askRemoveDevice()
            }

            is SetDeviceRemovedResult -> {
                setResult(RESULT_DEVICE_REMOVED, EditDeviceContract.resultIntent(currentDevice))
            }

            is AskTurnOffSync -> {
                askTurnOffSync()
            }

            is SetSyncTurnedOffResult -> {
                setResult(RESULT_SYNC_TURNED_OFF, EditDeviceContract.resultIntent(currentDevice))
            }

            is ResetTurnOffSyncToggle -> {
                binding.syncThisDeviceToggle.quietlySetIsChecked(true, turnOffSyncListener)
            }

            is ShowError -> {
                showError(command)
            }

            is Close -> {
                finish()
            }
        }
    }

    private fun askEditDevice() {
        val inputBinding = DialogEditDeviceBinding.inflate(layoutInflater).apply {
            customDialogTextInput.text = currentDevice.deviceName
        }
        CustomAlertDialogBuilder(this)
            .setTitle(R.string.sync_device_v2_edit_device_dialog_title)
            .setPositiveButton(R.string.sync_device_v2_edit_device_dialog_primary_button)
            .setNegativeButton(R.string.sync_device_v2_edit_device_dialog_secondary_button)
            .setView(inputBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.confirmNewDeviceName(inputBinding.customDialogTextInput.text)
                    }
                },
            )
            .show()
    }

    private fun askRemoveDevice() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_device_v2_remove_device_dialog_title)
            .setMessage(getString(R.string.sync_device_v2_remove_device_dialog_body, currentDevice.deviceName))
            .setPositiveButton(R.string.sync_device_v2_remove_device_dialog_primary_button)
            .setNegativeButton(R.string.sync_device_v2_remove_device_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onRemoveDeviceConfirmed()
                    }
                },
            )
            .show()
    }

    private fun askTurnOffSync() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_device_v2_turn_off_sync_dialog_title)
            .setMessage(getString(R.string.sync_device_v2_turn_off_sync_dialog_body))
            .setPositiveButton(R.string.sync_device_v2_turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.sync_device_v2_turn_off_sync_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onTurnOffSyncConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onTurnOffSyncCanceled()
                    }

                    override fun onDialogCancelled() {
                        viewModel.onTurnOffSyncCanceled()
                    }
                },
            )
            .show()
    }

    private fun showError(command: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(command.message) + "\n" + command.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .show()
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            viewModel.onCloseClicked()
        }
    }

    private fun configureEditDeviceNameItem() {
        binding.editThisDeviceNameItem.setOnClickListener {
            viewModel.onEditDeviceName()
        }
    }

    private fun configureTurnOffSyncToggle() {
        binding.syncThisDeviceToggle.apply {
            setIsChecked(true)
            setOnCheckedChangeListener(turnOffSyncListener)
        }
    }

    private fun configureRemoveAnotherDeviceItem() {
        val color = ColorStateList.valueOf(getColorFromAttr(CommonR.attr.daxColorDestructive))
        binding.removeAnotherDeviceItem.apply {
            leadingIcon().imageTintList = color
            setPrimaryTextColorStateList(color)
            setOnClickListener { viewModel.onRemoveDevice() }
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
