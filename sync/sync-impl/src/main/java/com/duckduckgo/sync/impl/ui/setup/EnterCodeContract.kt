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
import com.duckduckgo.sync.impl.ui.EnterCodeActivity
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.EXTRA_USER_SWITCHED_ACCOUNT
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract.EnterCodeContractOutput

class EnterCodeContract : ActivityResultContract<Code, EnterCodeContractOutput>() {
    override fun createIntent(
        context: Context,
        codeType: Code,
    ): Intent {
        return EnterCodeActivity.intent(context, codeType)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): EnterCodeContractOutput {
        when {
            resultCode == Activity.RESULT_OK -> {
                val userSwitchedAccount = intent?.getBooleanExtra(EXTRA_USER_SWITCHED_ACCOUNT, false) ?: false
                return if (userSwitchedAccount) {
                    EnterCodeContractOutput.SwitchAccountSuccess
                } else {
                    EnterCodeContractOutput.LoginSuccess
                }
            }

            else -> return EnterCodeContractOutput.Error
        }
    }

    sealed class EnterCodeContractOutput {
        data object LoginSuccess : EnterCodeContractOutput()
        data object SwitchAccountSuccess : EnterCodeContractOutput()
        data object Error : EnterCodeContractOutput()
    }
}
