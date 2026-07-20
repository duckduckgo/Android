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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.duckduckgo.sync.api.DeviceSyncState.Type
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncV2DeviceHeaderBinding

class DeviceHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val binding = ViewSyncV2DeviceHeaderBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
    }

    fun setState(
        device: ConnectedDevice,
    ) {
        binding.image.setImageResource(device.deviceType.type().toHeaderImageRes())
        binding.headlineText.text = device.deviceName
    }
}

private fun Type.toHeaderImageRes() = when (this) {
    Type.MOBILE -> R.drawable.ic_header_synced_device_mobile
    Type.DESKTOP -> R.drawable.ic_header_synced_device_desktop
    Type.UNKNOWN -> R.drawable.ic_header_synced_device_mobile
}
