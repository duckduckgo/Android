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
import com.duckduckgo.browser.api.ui.BrowserScreens.FeedbackActivityWithEmptyParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject

class AppFeedbackContract @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) : ActivityResultContract<Void?, Boolean>() {
    override fun createIntent(
        context: Context,
        input: Void?,
    ): Intent {
        return globalActivityStarter.startIntent(context, FeedbackActivityWithEmptyParams)!!
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        return resultCode == RESULT_OK
    }
}
