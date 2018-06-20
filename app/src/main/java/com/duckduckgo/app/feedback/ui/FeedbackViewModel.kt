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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.duckduckgo.app.feedback.api.FeedbackSender
import com.duckduckgo.app.global.SingleLiveEvent


class FeedbackViewModel(private val feedbackSender: FeedbackSender) : ViewModel() {

    data class ViewState(
        val isBrokenSite: Boolean = false,
        val url: String? = null,
        val showUrl: Boolean = false,
        val message: String? = null,
        val submitAllowed: Boolean = false
    )

    sealed class Command {
        object FocusUrl : Command()
        object FocusMessage : Command()
        object Finish : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val viewValue: ViewState get() = viewState.value!!

    init {
        viewState.value = ViewState()
    }

    fun setInitialBrokenSite(url: String?) {
        onBrokenSiteUrlChanged(url)
        onBrokenSiteChanged(true)
    }

    fun onBrokenSiteChanged(isBroken: Boolean) {
        if (isBroken == viewState.value?.isBrokenSite) {
            return
        }

        viewState.value = viewState.value?.copy(
            isBrokenSite = isBroken,
            showUrl = isBroken,
            submitAllowed = canSubmit(isBroken, viewValue.url, viewValue.message)
        )

        if (isBroken && viewValue.url.isNullOrBlank()) {
            command.value = Command.FocusUrl
        } else {
            command.value = Command.FocusMessage
        }
    }

    fun onBrokenSiteUrlChanged(newUrl: String?) {
        viewState.value = viewState.value?.copy(
            url = newUrl,
            submitAllowed = canSubmit(viewValue.isBrokenSite, newUrl, viewValue.message)
        )
    }

    fun onFeedbackMessageChanged(newMessage: String?) {
        viewState.value = viewState.value?.copy(
            message = newMessage,
            submitAllowed = canSubmit(viewValue.isBrokenSite, viewValue.url, newMessage)
        )
    }


    private fun canSubmit(isBrokenSite: Boolean, url: String?, feedbackMessage: String?): Boolean {
        return (isBrokenSite && !url.isNullOrBlank()) || (!isBrokenSite && !feedbackMessage.isNullOrBlank())
    }

    fun onSubmitPressed() {
        if (viewValue.isBrokenSite) {
            val url = viewValue.url ?: return
            feedbackSender.submitBrokenSiteFeedback(viewValue.message, url)
        } else {
            val message = viewValue.message ?: return
            feedbackSender.submitGeneralFeedback(message)
        }

        command.value = Command.Finish
    }

}