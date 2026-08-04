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
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.OriginalFlow
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeContract.Input
import com.duckduckgo.sync.impl.ui.v2.ExchangeSyncCodeContract.Output

class ExchangeSyncCodeContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return ExchangeSyncCodeActivity.intent(
            context = context,
            syncUrl = input.syncUrl,
            launchSource = input.launchSource,
            originalFlow = input.originalFlow,
        )
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        return when (resultCode) {
            SyncPairingResult.RESULT_SYNC_COMPLETED -> {
                intent
                    ?.let(SyncPairingResult::fromIntent)
                    ?.let(Output::SyncCompleted)
                    ?: Output.Dismissed
            }

            else -> Output.Dismissed
        }
    }

    data class Input(
        val syncUrl: String,
        val launchSource: String?,
        val originalFlow: OriginalFlow,
    )

    sealed interface Output {
        data class SyncCompleted(
            val result: SyncPairingResult,
        ) : Output

        data object Dismissed : Output
    }
}
