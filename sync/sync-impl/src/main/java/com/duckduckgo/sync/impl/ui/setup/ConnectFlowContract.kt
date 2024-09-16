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
import com.duckduckgo.sync.impl.ui.SyncConnectActivity

internal class ConnectFlowContract : ActivityResultContract<ConnectFlowContractInput, Boolean>() {
    override fun createIntent(
        context: Context,
        input: ConnectFlowContractInput,
    ): Intent {
        return SyncConnectActivity.intent(context, input.launchSource)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}

internal data class ConnectFlowContractInput(val launchSource: String? = null)
