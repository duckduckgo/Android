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

package com.duckduckgo.app.feedback.ui.positive.initial

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent


class PositiveFeedbackLandingViewModel : ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun userSelectedToRateApp() {
        command.value = Command.LaunchPlayStore
    }

    fun userSelectedToProvideFeedbackDetails() {
        command.value = Command.LaunchShareFeedbackPage
    }

    fun userFinishedGivingPositiveFeedback() {
        command.value = Command.Exit
    }
}

data class ViewState(val canShowRatingButton: Boolean)

sealed class Command {
    object LaunchPlayStore : Command()
    object Exit : Command()
    object LaunchShareFeedbackPage : Command()
}