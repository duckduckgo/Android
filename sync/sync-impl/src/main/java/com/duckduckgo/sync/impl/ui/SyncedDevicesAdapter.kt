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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.R.layout
import com.duckduckgo.sync.impl.asDrawableRes
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceLoadingBinding
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice

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
            else -> throw IllegalArgumentException("Unknown view type")
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
        val oldData = this.syncedDevices
        val diffResult = DiffCallback(oldData, data).run { DiffUtil.calculateDiff(this) }
        this.syncedDevices.clear().also { this.syncedDevices.addAll(data) }
        diffResult.dispatchUpdatesTo(this)
    }

    private class DiffCallback(
        private val old: List<SyncDeviceListItem>,
        private val new: List<SyncDeviceListItem>,
    ) : DiffUtil.Callback() {
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
                binding.remoteSyncDevice.gone()
                binding.localSyncDevice.setLeadingIconResource(deviceType.type().asDrawableRes())
                binding.localSyncDevice.setPrimaryText(deviceName)
                binding.localSyncDevice.setSecondaryText(context.getString(R.string.sync_device_this_device_hint))
                binding.localSyncDevice.setTrailingIconClickListener {
                    showLocalOverFlowMenu(it, syncDevice)
                }
            } else {
                binding.localSyncDevice.gone()
                binding.remoteSyncDevice.show()
                binding.remoteSyncDevice.setLeadingIconResource(deviceType.type().asDrawableRes())
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
        val popupMenu = PopupMenu(
            layoutInflater,
            layout.popup_windows_edit_device_menu,
        )
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
        val popupMenu = PopupMenu(layoutInflater, layout.popup_windows_remove_device_menu)
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

sealed class SyncDeviceListItem {
    data class SyncedDevice(
        val device: ConnectedDevice,
        val loading: Boolean = false,
    ) : SyncDeviceListItem()

    data object LoadingItem : SyncDeviceListItem()
}

interface ConnectedDeviceClickListener {
    fun onEditDeviceClicked(device: ConnectedDevice)
    fun onRemoveDeviceClicked(device: ConnectedDevice)
}
