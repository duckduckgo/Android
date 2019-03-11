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

package com.duckduckgo.app.feedback.ui.initial

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.ui.common.ViewState
import timber.log.Timber

class InitialFeedbackFragmentViewModel : ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: MutableLiveData<Command> = MutableLiveData()

    fun onPositiveFeedback() {
        Timber.i("User is giving positive feedback")
        command.value = Command.PositiveFeedbackSelected
    }

    fun onNegativeFeedback() {
        Timber.i("User is giving negative feedback")
        command.value = Command.NegativeFeedbackSelected
    }

    sealed class Command {
        object PositiveFeedbackSelected : Command()
        object NegativeFeedbackSelected : Command()
        object UserCancelled : Command()
    }
}
