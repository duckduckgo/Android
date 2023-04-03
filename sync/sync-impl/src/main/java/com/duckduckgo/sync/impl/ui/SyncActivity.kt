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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.asDrawableRes
import com.duckduckgo.sync.impl.databinding.ActivitySyncBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceLoadingBinding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
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
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed class SyncDeviceListItem {
    data class SyncedDevice(val device: ConnectedDevice) : SyncDeviceListItem()
    object LoadingItem : SyncDeviceListItem()
}

class SyncedDevicesAdapter constructor(): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var syncedDevices = mutableListOf<SyncDeviceListItem>()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            CONNECTED_DEVICE -> SyncedDeviceViewHolder(ItemSyncDeviceBinding.inflate(inflater, parent, false))
            LOADING_ITEM -> LoadingViewHolder(ItemSyncDeviceLoadingBinding.inflate(inflater, parent, false))
            else -> {
                // This should never happen
                return SyncedDeviceViewHolder(ItemSyncDeviceBinding.inflate(inflater, parent, false))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (syncedDevices[position]) {
            is SyncedDevice -> CONNECTED_DEVICE
            is SyncDeviceListItem.LoadingItem -> LOADING_ITEM
        }
    }

    override fun getItemCount(): Int {
        return this.syncedDevices.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        when(holder) {
            is SyncedDeviceViewHolder -> {
                holder.bind(syncedDevices[position] as SyncedDevice)
            }
            is LoadingViewHolder -> {
                holder.bind()
            }
        }
    }

    fun updateData(data: List<SyncDeviceListItem>) {
        val newData = data
        val oldData = this.syncedDevices
        val diffResult = DiffCallback(oldData, newData).run { DiffUtil.calculateDiff(this) }
        this.syncedDevices.clear().also { this.syncedDevices.addAll(data) }
        diffResult.dispatchUpdatesTo(this)
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

class SyncedDeviceViewHolder(val binding: ItemSyncDeviceBinding): RecyclerView.ViewHolder(binding.root) {
    private val context = binding.root.context
    fun bind(syncDevice: SyncedDevice) {
        with(syncDevice.device) {
            binding.syncDevice.setLeadingIcon(deviceType.type().asDrawableRes())
            binding.syncDevice.setPrimaryText(deviceName)
            if (thisDevice) {
                binding.syncDevice.setSecondaryText(context.getString(R.string.sync_device_this_device_hint))
            }
        }
    }
}

class LoadingViewHolder(val binding: ItemSyncDeviceLoadingBinding): RecyclerView.ViewHolder(binding.root) {
    fun bind() {
        binding.shimmerFrameLayout.startShimmer()
    }
}

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SyncActivityWithEmptyParams::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncBinding by viewBinding()
    private val viewModel: SyncActivityViewModel by bindViewModel()

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var storagePermission: PermissionRequest

    @Inject
    lateinit var shareAction: ShareAction

    private val syncedDevicesAdapter = SyncedDevicesAdapter()
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
            AskTurnOffSync -> askTurnOffsync()
            AskDeleteAccount -> askDeleteAccount()
            is RecoveryCodePDFSuccess -> {
                shareAction.shareFile(this@SyncActivity, it.recoveryCodePDFFile)
            }
            CheckIfUserHasStoragePermission -> {
                storagePermission.invokeOrRequestPermission {
                    viewModel.generateRecoveryCode(this@SyncActivity)
                }
            }
        }
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

    private fun askTurnOffsync() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onTurnOffSyncConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onTurnOffSyncCancelled()
                    }
                },
            ).show()
    }

    private fun renderViewState(viewState: ViewState) {
        binding.deviceSyncStatusToggle.quietlySetIsChecked(viewState.isDeviceSyncEnabled, deviceSyncStatusToggleListener)
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
            syncedDevicesAdapter.updateData(viewState.syncedDevices)
        }
    }
}
