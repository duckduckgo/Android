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
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyContract.Input
import com.duckduckgo.sync.impl.ui.v2.PreviousSessionReadyContract.Output

class PreviousSessionReadyContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return PreviousSessionReadyActivity.intent(context, input.syncEntryPoint)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        return when (resultCode) {
            RESULT_RESUME -> {
                intent
                    ?.getStringExtra(RECOVERY_CODE_EXTRA_KEY)
                    ?.let(Output::Resume)
                    ?: Output.Dismissed
            }

            RESULT_CONTINUE_SETUP -> {
                intent
                    ?.let { IntentCompat.getSerializableExtra(it, ORIGINAL_FLOW_EXTRA_KEY, SyncEntryPoint::class.java) }
                    ?.let(Output::ContinueSetup)
                    ?: Output.Dismissed
            }

            else -> Output.Dismissed
        }
    }

    data class Input(
        val syncEntryPoint: SyncEntryPoint,
    )

    sealed interface Output {
        data class Resume(
            val recoveryCode: String,
        ) : Output

        data class ContinueSetup(
            val syncEntryPoint: SyncEntryPoint,
        ) : Output

        data object Dismissed : Output
    }

    companion object {
        internal const val RESULT_CONTINUE_SETUP = 220
        internal const val RESULT_RESUME = 221
        private const val ORIGINAL_FLOW_EXTRA_KEY = "original_flow"
        private const val RECOVERY_CODE_EXTRA_KEY = "recovery_code"

        internal fun resumeResultIntent(recoveryCode: String): Intent =
            Intent().putExtra(RECOVERY_CODE_EXTRA_KEY, recoveryCode)

        internal fun continueSetupResultIntent(syncEntryPoint: SyncEntryPoint): Intent =
            Intent().putExtra(ORIGINAL_FLOW_EXTRA_KEY, syncEntryPoint)
    }
}
