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
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceContract.Input
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceContract.Output
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceContract.Output.BackedUp
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceContract.Output.Dismissed
import com.duckduckgo.sync.impl.ui.v2.SyncThisDeviceContract.Output.SyncedWithAnotherDevice

class SyncThisDeviceContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return SyncThisDeviceActivity.intent(context, input.source)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        return when (resultCode) {
            RESULT_DEVICE_BACKED_UP -> {
                val device = intent
                    ?.let { IntentCompat.getParcelableExtra(it, DEVICE_EXTRA_KEY, ParcelableDevice::class.java) }
                    ?.toConnectedDevice()
                if (device != null) BackedUp(device) else Dismissed
            }

            SyncPairingResult.RESULT_SYNC_COMPLETED ->
                intent?.let(SyncPairingResult::fromIntent)?.let(::SyncedWithAnotherDevice) ?: Dismissed

            else -> Dismissed
        }
    }

    data class Input(
        val source: String?,
    )

    sealed interface Output {
        data class BackedUp(
            val device: ConnectedDevice,
        ) : Output

        data class SyncedWithAnotherDevice(
            val result: SyncPairingResult,
        ) : Output

        data object Dismissed : Output
    }

    companion object {
        const val DEVICE_EXTRA_KEY = "device"
        const val RESULT_DEVICE_BACKED_UP = 200

        fun resultIntent(device: ConnectedDevice) = Intent().putExtra(DEVICE_EXTRA_KEY, ParcelableDevice.fromConnectedDevice(device))
    }
}
