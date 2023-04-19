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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.R.layout
import com.duckduckgo.sync.impl.asDrawableRes
import com.duckduckgo.sync.impl.databinding.ActivitySyncBinding
import com.duckduckgo.sync.impl.databinding.DialogEditDeviceBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceLoadingBinding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity
import com.google.android.material.snackbar.Snackbar
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed class SyncDeviceListItem {
    data class SyncedDevice(val device: ConnectedDevice, val loading: Boolean = false) : SyncDeviceListItem()
    object LoadingItem : SyncDeviceListItem()
}

interface ConnectedDeviceClickListener {
    fun onEditDeviceClicked(device: ConnectedDevice)
    fun onRemoveDeviceClicked(device: ConnectedDevice)
}

class SyncedDevicesAdapter constructor(private val listener: ConnectedDeviceClickListener) : RecyclerView.Adapter<ViewHolder>() {

    private var syncedDevices = mutableListOf<SyncDeviceListItem>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            CONNECTED_DEVICE -> SyncedDeviceViewHolder(
                inflater,
                ItemSyncDeviceBinding.inflate(inflater, parent, false),
                listener,
            )
            LOADING_ITEM -> LoadingViewHolder(ItemSyncDeviceLoadingBinding.inflate(inflater, parent, false))
            else -> {
                // This should never happen
                return SyncedDeviceViewHolder(
                    inflater,
                    ItemSyncDeviceBinding.inflate(inflater, parent, false),
                    listener,
                )
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val device = syncedDevices[position]) {
            is SyncedDevice -> {
                if (device.loading) {
                    LOADING_ITEM
                } else {
                    CONNECTED_DEVICE
                }
            }
            is SyncDeviceListItem.LoadingItem -> LOADING_ITEM
        }
    }

    override fun getItemCount(): Int {
        return this.syncedDevices.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is SyncedDeviceViewHolder -> {
                holder.bind(syncedDevices[position] as SyncedDevice)
            }

            is LoadingViewHolder -> {
                holder.bind()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(data: List<SyncDeviceListItem>) {
        this.syncedDevices.clear().also { this.syncedDevices.addAll(data) }
        notifyDataSetChanged() // there's a weird bug when using DiffUtil with duplicated devices after login, need to investigate
    }

    private class DiffCallback(
        private val old: List<SyncDeviceListItem>,
        private val new: List<SyncDeviceListItem>,
    ) :
        DiffUtil.Callback() {
        override fun getOldListSize() = old.size

        override fun getNewListSize() = new.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            if (old[oldItemPosition] is SyncedDevice && new[newItemPosition] is SyncedDevice) {
                return (old[oldItemPosition] as SyncedDevice).device.deviceId == (new[newItemPosition] as SyncedDevice).device.deviceId
            }
            return old[oldItemPosition] == new[newItemPosition]
        }

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }
    }

    companion object {
        private const val CONNECTED_DEVICE = 0
        private const val LOADING_ITEM = 1
    }
}

class SyncedDeviceViewHolder(
    private val layoutInflater: LayoutInflater,
    private val binding: ItemSyncDeviceBinding,
    private val listener: ConnectedDeviceClickListener,
) : ViewHolder(binding.root) {
    private val context = binding.root.context
    fun bind(syncDevice: SyncedDevice) {
        with(syncDevice.device) {
            if (thisDevice) {
                binding.localSyncDevice.show()
                binding.localSyncDevice.setLeadingIcon(deviceType.type().asDrawableRes())
                binding.localSyncDevice.setPrimaryText(deviceName)
                binding.localSyncDevice.setSecondaryText(context.getString(R.string.sync_device_this_device_hint))
                binding.localSyncDevice.setTrailingIconClickListener {
                    showLocalOverFlowMenu(it, syncDevice)
                }
            } else {
                binding.remoteSyncDevice.show()
                binding.remoteSyncDevice.setLeadingIcon(deviceType.type().asDrawableRes())
                binding.remoteSyncDevice.setPrimaryText(deviceName)
                binding.remoteSyncDevice.setTrailingIconClickListener {
                    showRemoteOverFlowMenu(it, syncDevice)
                }
            }
        }
    }

    private fun showLocalOverFlowMenu(
        anchor: View,
        syncDevice: SyncedDevice,
    ) {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_windows_edit_device_menu)
        val view = popupMenu.contentView
        popupMenu.apply {
            onMenuItemClicked(view.findViewById(R.id.edit)) { listener.onEditDeviceClicked(syncDevice.device) }
        }
        popupMenu.show(binding.root, anchor)
    }

    private fun showRemoteOverFlowMenu(
        anchor: View,
        syncDevice: SyncedDevice,
    ) {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_windows_remove_device_menu)
        val view = popupMenu.contentView
        popupMenu.apply {
            onMenuItemClicked(view.findViewById(R.id.remove)) { listener.onRemoveDeviceClicked(syncDevice.device) }
        }
        popupMenu.show(binding.root, anchor)
    }
}

class LoadingViewHolder(val binding: ItemSyncDeviceLoadingBinding) : ViewHolder(binding.root) {
    fun bind() {
        binding.shimmerFrameLayout.startShimmer()
    }
}

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncActivityWithEmptyParams::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncBinding by viewBinding()
    private val viewModel: SyncActivityViewModel by bindViewModel()

    private val syncedDevicesAdapter = SyncedDevicesAdapter(
        object : ConnectedDeviceClickListener {
            override fun onEditDeviceClicked(device: ConnectedDevice) {
                viewModel.onEditDeviceClicked(device)
            }

        override fun onRemoveDeviceClicked(device: ConnectedDevice) {
            viewModel.onRemoveDeviceClicked(device)
        }
    })

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var storagePermission: PermissionRequest

    @Inject
    lateinit var shareAction: ShareAction

    private val deviceSyncStatusToggleListener: OnCheckedChangeListener = object : OnCheckedChangeListener {
        override fun onCheckedChanged(
            buttonView: CompoundButton?,
            isChecked: Boolean,
        ) {
            viewModel.onToggleClicked(isChecked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        registerForPermission()
        setupRecyclerView()
    }

    private fun registerForPermission() {
        storagePermission.registerResultsCallback(this) {
            binding.root.makeSnackbarWithNoBottomInset(R.string.sync_permission_required_store_recovery_code, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        with(binding.syncedDevicesRecyclerView) {
            adapter = syncedDevicesAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.getSyncState()
    }

    private fun observeUiEvents() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands().flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).onEach { processCommand(it) }.launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            LaunchDeviceSetupFlow -> {
                startActivity(SetupAccountActivity.intentStartSetupFlow(this))
            }

            is AskTurnOffSync -> askTurnOffsync(it.device)
            AskDeleteAccount -> askDeleteAccount()
            is RecoveryCodePDFSuccess -> {
                shareAction.shareFile(this@SyncActivity, it.recoveryCodePDFFile)
            }
            CheckIfUserHasStoragePermission -> {
                storagePermission.invokeOrRequestPermission {
                    viewModel.generateRecoveryCode(this@SyncActivity)
                }
            }
            is AskRemoveDevice -> askRemoveDevice(it.device)
            is AskEditDevice -> askEditDevice(it.device)
        }
    }

    private fun askEditDevice(device: ConnectedDevice) {
        val inputBinding = DialogEditDeviceBinding.inflate(layoutInflater)
        inputBinding.customDialogTextInput.text = device.deviceName
        CustomAlertDialogBuilder(this)
            .setTitle(R.string.edit_device_dialog_title)
            .setPositiveButton(R.string.edit_device_dialog_primary_button)
            .setNegativeButton(R.string.edit_device_dialog_secondary_button)
            .setView(inputBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val newText = inputBinding.customDialogTextInput.text
                        viewModel.onDeviceEdited(device.copy(deviceName = newText))
                    }
                },
            )
            .show()
    }

    private fun askRemoveDevice(device: ConnectedDevice) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.remove_device_dialog_title)
            .setMessage(getString(R.string.remove_device_dialog_content, device.deviceName))
            .setPositiveButton(R.string.remove_device_dialog_primary_button)
            .setNegativeButton(R.string.remove_device_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onRemoveDeviceConfirmed(device)
                    }
                },
            ).show()
    }

    private fun askDeleteAccount() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .setDestructiveButtons(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onDeleteAccountConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onDeleteAccountCancelled()
                    }
                },
            ).show()
    }

    private fun askTurnOffsync(device: ConnectedDevice) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onTurnOffSyncConfirmed(device)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onTurnOffSyncCancelled()
                    }
                },
            ).show()
    }

    private fun renderViewState(viewState: ViewState) {
        binding.deviceSyncStatusToggle.quietlySetIsChecked(viewState.syncToggleState, deviceSyncStatusToggleListener)
        binding.viewSwitcher.displayedChild = if (viewState.showAccount) 1 else 0

        if (viewState.showAccount) {
            if (viewState.loginQRCode != null) {
                binding.qrCodeImageView.show()
                binding.qrCodeImageView.setImageBitmap(viewState.loginQRCode)
            }

            binding.saveRecoveryCodeItem.setOnClickListener {
                viewModel.onSaveRecoveryCodeClicked()
            }

            binding.deleteAccountButton.setOnClickListener {
                viewModel.onDeleteAccountClicked()
            }
        }
        syncedDevicesAdapter.updateData(viewState.syncedDevices)
    }
}
