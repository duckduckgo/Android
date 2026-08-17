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
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeContract.Input
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeContract.Output
import kotlinx.parcelize.Parcelize

class ProcessSyncCodeContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return ProcessSyncCodeActivity.intent(
            context = context,
            codeSource = input.source,
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
        val source: SyncCodeSource,
    )

    sealed interface Output {
        data class SyncCompleted(
            val result: SyncPairingResult,
        ) : Output

        data object Dismissed : Output
    }
}

/** A [code] the user is setting up with, together with how it reached [ProcessSyncCodeActivity]. */
sealed interface SyncCodeSource : Parcelable {
    val code: String
    val entryPoint: SyncEntryPoint

    /** A code the user scanned with the camera. */
    @Parcelize
    data class Scanned(
        override val code: String,
        override val entryPoint: SyncEntryPoint,
    ) : SyncCodeSource

    /** A code the user pasted into manual entry. */
    @Parcelize
    data class Pasted(
        override val code: String,
        override val entryPoint: SyncEntryPoint,
    ) : SyncCodeSource

    /** A code that arrived from an external deep link. */
    @Parcelize
    data class DeepLink(
        override val code: String,
        override val entryPoint: SyncEntryPoint,
    ) : SyncCodeSource

    /** A stored recovery code replayed by the settings "restore previous session" path. */
    @Parcelize
    data class Restored(
        override val code: String,
    ) : SyncCodeSource {
        override val entryPoint: SyncEntryPoint get() = SyncEntryPoint.RECOVER_SYNCED_DATA
    }
}
