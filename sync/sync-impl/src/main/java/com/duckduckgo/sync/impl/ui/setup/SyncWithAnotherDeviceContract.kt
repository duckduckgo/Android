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

package com.duckduckgo.sync.impl.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.sync.impl.ui.SyncWithAnotherDeviceActivity
import com.duckduckgo.sync.impl.ui.SyncWithAnotherDeviceActivity.Companion.EXTRA_USER_SWITCHED_ACCOUNT
import com.duckduckgo.sync.impl.ui.setup.SyncWithAnotherDeviceContract.SyncWithAnotherDeviceContractOutput

/**
 * Input can be null if not required
 * Or, input can be a sync setup URL which includes the pairing code
 */
internal class SyncWithAnotherDeviceContract : ActivityResultContract<String?, SyncWithAnotherDeviceContractOutput>() {

    /**
     * @param input can be null if not required. or, input can be a sync setup URL which includes the pairing code
     */
    override fun createIntent(
        context: Context,
        input: String?,
    ): Intent {
        return if (input == null) {
            SyncWithAnotherDeviceActivity.intent(context)
        } else {
            SyncWithAnotherDeviceActivity.intentForDeepLink(context, input)
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): SyncWithAnotherDeviceContractOutput {
        when {
            resultCode == Activity.RESULT_OK -> {
                val userSwitchedAccount = intent?.getBooleanExtra(EXTRA_USER_SWITCHED_ACCOUNT, false) ?: false
                return if (userSwitchedAccount) {
                    SyncWithAnotherDeviceContractOutput.SwitchAccountSuccess
                } else {
                    SyncWithAnotherDeviceContractOutput.DeviceConnected
                }
            }
            else -> return SyncWithAnotherDeviceContractOutput.Error
        }
    }

    sealed class SyncWithAnotherDeviceContractOutput {
        data object DeviceConnected : SyncWithAnotherDeviceContractOutput()
        data object SwitchAccountSuccess : SyncWithAnotherDeviceContractOutput()
        data object Error : SyncWithAnotherDeviceContractOutput()
    }
}
