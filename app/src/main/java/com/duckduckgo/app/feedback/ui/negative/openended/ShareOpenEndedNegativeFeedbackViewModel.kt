/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui.negative.openended

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SubReason
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

class ShareOpenEndedNegativeFeedbackViewModel : ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun userSubmittingPositiveFeedback(feedback: String) {
        command.value = Command.ExitAndSubmitPositiveFeedback(feedback)
    }

    fun userSubmittingNegativeFeedback(
        mainReason: MainReason,
        subReason: SubReason?,
        openEndedComment: String
    ) {
        command.value =
            Command.ExitAndSubmitNegativeFeedback(mainReason, subReason, openEndedComment)
    }

    sealed class Command {
        data class ExitAndSubmitNegativeFeedback(
            val mainReason: MainReason,
            val subReason: SubReason?,
            val feedback: String
        ) : Command()
        data class ExitAndSubmitPositiveFeedback(val feedback: String) : Command()
        object Exit : Command()
    }
}

@ContributesMultibinding(AppScope::class)
class ShareOpenEndedNegativeFeedbackViewModelFactory @Inject constructor() :
    ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(ShareOpenEndedNegativeFeedbackViewModel::class.java) ->
                    (ShareOpenEndedNegativeFeedbackViewModel() as T)
                else -> null
            }
        }
    }
}
