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

package com.duckduckgo.sync.impl.ui.setup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.RESULT_CONTINUE_WITHOUT_RESTORE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.PREVIOUS_SESSION_READY
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyResult.Cancelled
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyResult.ContinueSetup
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyResult.Resumed

class SyncPreviousSessionReadyContract : ActivityResultContract<String?, SyncPreviousSessionReadyResult>() {
    override fun createIntent(context: Context, input: String?): Intent {
        return SetupAccountActivity.intent(context, PREVIOUS_SESSION_READY, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): SyncPreviousSessionReadyResult {
        return when (resultCode) {
            Activity.RESULT_OK -> Resumed
            RESULT_CONTINUE_WITHOUT_RESTORE -> ContinueSetup
            else -> Cancelled
        }
    }
}

sealed class SyncPreviousSessionReadyResult {
    data object Resumed : SyncPreviousSessionReadyResult()
    data object ContinueSetup : SyncPreviousSessionReadyResult()
    data object Cancelled : SyncPreviousSessionReadyResult()
}
