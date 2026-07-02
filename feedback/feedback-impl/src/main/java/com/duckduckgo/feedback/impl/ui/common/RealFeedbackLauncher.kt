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

package com.duckduckgo.feedback.impl.ui.common

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feedback.api.FeedbackLauncher
import com.duckduckgo.feedback.api.FeedbackScreenNoParams
import com.duckduckgo.feedback.impl.R
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealFeedbackLauncher @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) : FeedbackLauncher {

    override fun feedbackContract(): ActivityResultContract<Void?, Boolean> =
        FeedbackContract(globalActivityStarter)

    override fun showFeedbackSubmittedMessage(hostView: View) {
        Snackbar.make(hostView, R.string.thanksForTheFeedback, Snackbar.LENGTH_LONG).show()
    }
}

private class FeedbackContract(
    private val globalActivityStarter: GlobalActivityStarter,
) : ActivityResultContract<Void?, Boolean>() {

    override fun createIntent(
        context: Context,
        input: Void?,
    ): Intent = globalActivityStarter.startIntent(context, FeedbackScreenNoParams)!!

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean = resultCode == RESULT_OK
}
