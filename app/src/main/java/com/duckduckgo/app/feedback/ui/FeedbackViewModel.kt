/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.api.FeedbackSender
import com.duckduckgo.app.global.SingleLiveEvent
import timber.log.Timber


class FeedbackViewModel(private val feedbackSender: FeedbackSender) : ViewModel() {

    data class ViewState(
            val isBrokenSite: Boolean = false,
            val url: String? = null,
            val showUrl: Boolean = false,
            val message: String? = null,
            val submitAllowed: Boolean = false
    )

    sealed class Command {
        object ConfirmAndFinish : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val viewValue: ViewState
        get() = viewState.value!!

    init {
        viewState.value = ViewState()
    }

    fun onSubmitPressed() {

        val message = viewValue.message ?: return

        if (viewValue.isBrokenSite) {
            val url = viewValue.url ?: return
            feedbackSender.submitBrokenSiteFeedback(message, url)
        } else {
            feedbackSender.submitGeneralFeedback(message)
        }

        command.value = Command.ConfirmAndFinish
    }

    fun onPositiveFeedback() {
        Timber.i("User is giving positive feedback")
    }

    fun onNegativeFeedback() {
        Timber.i("User is giving negative feedback")
    }

    fun onReportBrokenSite() {
        Timber.i("User wants to report broken site")
    }

}