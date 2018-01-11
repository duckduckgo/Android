/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.net.Uri
import android.os.Build
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.StringResolver
import javax.inject.Inject

class SettingsViewModel @Inject constructor(private val stringResolver: StringResolver) : ViewModel() {

    data class ViewState(
            val loading: Boolean = true,
            val version: String = ""
    )

    sealed class Command {
        class SendEmail(val emailUri: Uri) : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: MutableLiveData<Command> = MutableLiveData()

    private var currentViewState: ViewState = ViewState()

    init {
        viewState.value = currentViewState
    }

    fun start() {
        viewState.value = currentViewState.copy(loading = false, version = obtainVersion())
    }

    fun userRequestedToSendFeedback() {
        val emailAddress = stringResolver.getString(R.string.feedbackEmailAddress)
        val subject = encode { stringResolver.getString(R.string.feedbackSubject) }
        val body = encode { buildEmailBody() }

        val uri = "mailto:$emailAddress?&subject=$subject&body=$body"
        command.value = Command.SendEmail(Uri.parse(uri))
    }

    private fun buildEmailBody(): String {
        return "App Version: ${obtainVersion()}\n" +
                "Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Manufacturer: ${Build.MANUFACTURER}\n" +
                "Model: ${Build.MODEL}\n" +
                "\n\n\n"
    }

    private fun obtainVersion(): String {
        return "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private inline fun encode(f: () -> String): String = Uri.encode(f())

}