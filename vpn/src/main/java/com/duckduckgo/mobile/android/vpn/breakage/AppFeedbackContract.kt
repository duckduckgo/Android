/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class AppFeedbackContract : ActivityResultContract<Void?, Boolean>() {
    override fun createIntent(context: Context, input: Void?): Intent {
        // TODO Class.forName is not great but required to unblock ATP for now
        // We need to make bigger refactors to extract features into its own gradle modules
        // so that they are accessible from other places.
        return Intent(
            context, Class.forName("com.duckduckgo.app.feedback.ui.common.FeedbackActivity"))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == RESULT_OK
    }
}
