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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.asDrawableRes
import com.duckduckgo.sync.impl.databinding.ItemSyncDeviceLoadingBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncV2DeviceLocalBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncV2DeviceRemoteBinding
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.LoadingItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice

class SyncedDeviceAdapter : ListAdapter<SyncDeviceListItem, ViewHolder>(ItemCallback) {
    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is LoadingItem -> R.layout.item_sync_device_loading
        is SyncedDevice -> when {
            item.loading -> R.layout.item_sync_device_loading
            item.device.thisDevice -> R.layout.item_sync_v2_device_local
            else -> R.layout.item_sync_v2_device_remote
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_sync_v2_device_local -> {
                LocalDeviceViewHolder(ItemSyncV2DeviceLocalBinding.inflate(inflater, parent, false))
            }

            R.layout.item_sync_v2_device_remote -> {
                RemoteDeviceViewHolder(ItemSyncV2DeviceRemoteBinding.inflate(inflater, parent, false))
            }

            R.layout.item_sync_device_loading -> {
                LoadingDeviceViewHolder(ItemSyncDeviceLoadingBinding.inflate(inflater, parent, false))
            }

            else -> error("Unknown view type: ${parent.context.resources.getResourceName(viewType)}")
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is LocalDeviceViewHolder -> holder.bind((getItem(position) as SyncedDevice).device)
            is RemoteDeviceViewHolder -> holder.bind((getItem(position) as SyncedDevice).device)
        }
    }
}

private object ItemCallback : DiffUtil.ItemCallback<SyncDeviceListItem>() {
    override fun areItemsTheSame(
        oldItem: SyncDeviceListItem,
        newItem: SyncDeviceListItem,
    ): Boolean {
        return if (oldItem is SyncedDevice && newItem is SyncedDevice) {
            oldItem.device.deviceId == newItem.device.deviceId
        } else {
            oldItem == newItem
        }
    }

    override fun areContentsTheSame(
        oldItem: SyncDeviceListItem,
        newItem: SyncDeviceListItem,
    ): Boolean {
        return oldItem == newItem
    }
}

private class LocalDeviceViewHolder(
    private val binding: ItemSyncV2DeviceLocalBinding,
) : ViewHolder(binding.root) {
    init {
        binding.root.setSecondaryText(itemView.context.getString(R.string.sync_device_this_device_hint))
    }

    fun bind(device: ConnectedDevice) {
        binding.root.setLeadingIconResource(device.deviceType.type().asDrawableRes())
        binding.root.setPrimaryText(device.deviceName)
    }
}

private class RemoteDeviceViewHolder(
    private val binding: ItemSyncV2DeviceRemoteBinding,
) : ViewHolder(binding.root) {
    fun bind(device: ConnectedDevice) {
        binding.root.setPrimaryText(device.deviceName)
        binding.root.setLeadingIconResource(device.deviceType.type().asDrawableRes())
    }
}

private class LoadingDeviceViewHolder(
    binding: ItemSyncDeviceLoadingBinding,
) : ViewHolder(binding.root)
