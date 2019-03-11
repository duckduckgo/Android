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

package com.duckduckgo.app.feedback.ui.positive.openended

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import timber.log.Timber


class ShareOpenEndedNegativeFeedbackViewModel : ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    private val currentViewState: ViewState
        get() = viewState.value!!

    val command: SingleLiveEvent<Command> = SingleLiveEvent()


    fun userSubmittingFeedback(feedback: String) {
        Timber.i("Received feedback: {$feedback}")
        command.value = Command.ExitAndSubmit(feedback)
    }

    sealed class ViewState {

    }

    sealed class Command {
        data class ExitAndSubmit(val feedback: String) : Command()
        object Exit : Command()
    }
}

