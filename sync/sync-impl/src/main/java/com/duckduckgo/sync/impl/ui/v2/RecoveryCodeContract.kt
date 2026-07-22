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

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeContract.Input
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeContract.Output

class RecoveryCodeContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return RecoveryCodeActivity.intent(context, input.deviceName)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                val isAutoRestoreEnabled = intent?.getBooleanExtra(IS_AUTO_RESTORE_ENABLED_KEY, true) ?: true
                Output.CodeGenerated(isAutoRestoreEnabled)
            }

            Activity.RESULT_CANCELED -> Output.Failure
            else -> Output.Failure
        }
    }

    data class Input(
        val deviceName: String,
    )

    sealed interface Output {
        data class CodeGenerated(
            val isAutoRestoreEnabled: Boolean,
        ) : Output

        data object Failure : Output
    }

    companion object {
        private const val IS_AUTO_RESTORE_ENABLED_KEY = "is_auto_restore_enabled"

        fun resultIntent(
            isAutoRestoreEnabled: Boolean,
        ): Intent {
            return Intent().putExtra(IS_AUTO_RESTORE_ENABLED_KEY, isAutoRestoreEnabled)
        }
    }
}
