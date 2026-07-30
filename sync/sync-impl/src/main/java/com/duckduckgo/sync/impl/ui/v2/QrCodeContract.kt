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
import com.duckduckgo.sync.impl.ui.v2.QrCodeContract.Input
import com.duckduckgo.sync.impl.ui.v2.QrCodeContract.Output
import com.duckduckgo.sync.impl.ui.v2.QrCodeContract.Output.Failure
import com.duckduckgo.sync.impl.ui.v2.QrCodeContract.Output.NoOp
import com.duckduckgo.sync.impl.ui.v2.QrCodeContract.Output.Success

class QrCodeContract : ActivityResultContract<Input, Output>() {
    override fun createIntent(
        context: Context,
        input: Input,
    ): Intent {
        return QrCodeActivity.intent(context, input.source)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Output {
        return when (resultCode) {
            RESULT_SYNC_SUCCESS -> Success
            RESULT_SYNC_FAILURE -> Failure
            else -> NoOp
        }
    }

    data class Input(
        val source: String?,
    )

    sealed interface Output {
        data object Success : Output

        data object Failure : Output

        data object NoOp : Output
    }

    companion object {
        const val RESULT_SYNC_SUCCESS = 200
        const val RESULT_SYNC_FAILURE = 201
    }
}
