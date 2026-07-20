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
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import kotlinx.parcelize.Parcelize

class EditDeviceContract : ActivityResultContract<EditDeviceContract.Input, EditDeviceContract.Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return EditDeviceActivity.intent(context, ParcelableDevice.fromConnectedDevice(input.device))
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        val device = intent
            ?.let { IntentCompat.getParcelableExtra(it, DEVICE_KEY, ParcelableDevice::class.java) }
            ?.toConnectedDevice()
            ?: return Output.NoOp

        return when (resultCode) {
            RESULT_DEVICE_EDITED -> Output.EditDevice(device)
            RESULT_DEVICE_REMOVED -> Output.RemoveDevice(device)
            RESULT_SYNC_TURNED_OFF -> Output.TurnOffSync(device)
            else -> Output.NoOp
        }
    }

    data class Input(
        val device: ConnectedDevice,
    )

    sealed interface Output {
        data class EditDevice(
            val device: ConnectedDevice,
        ) : Output

        data class TurnOffSync(
            val device: ConnectedDevice,
        ) : Output

        data class RemoveDevice(
            val device: ConnectedDevice,
        ) : Output

        data object NoOp : Output
    }

    companion object {
        const val DEVICE_KEY = "device"
        const val RESULT_DEVICE_EDITED = 200
        const val RESULT_DEVICE_REMOVED = 201
        const val RESULT_SYNC_TURNED_OFF = 202

        fun resultIntent(device: ConnectedDevice) = Intent().putExtra(DEVICE_KEY, ParcelableDevice.fromConnectedDevice(device))
    }
}

@Parcelize
data class ParcelableDevice(
    val id: String,
    val name: String,
    val factor: String,
    val isThisDevice: Boolean,
) : Parcelable {
    fun toConnectedDevice() = ConnectedDevice(
        deviceId = id,
        deviceName = name,
        deviceType = DeviceType(factor),
        thisDevice = isThisDevice,
    )

    companion object {
        fun fromConnectedDevice(device: ConnectedDevice) = ParcelableDevice(
            id = device.deviceId,
            name = device.deviceName,
            factor = device.deviceType.deviceFactor,
            isThisDevice = device.thisDevice,
        )
    }
}
