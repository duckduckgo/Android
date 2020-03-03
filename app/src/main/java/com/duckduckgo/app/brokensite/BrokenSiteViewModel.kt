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

package com.duckduckgo.app.brokensite

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.isMobileSite
import com.duckduckgo.app.statistics.pixels.Pixel


class BrokenSiteViewModel(private val pixel: Pixel, private val brokenSiteSender: BrokenSiteSender) : ViewModel() {

    data class ViewState(
        val url: String? = null,
        val message: String? = null,
        val submitAllowed: Boolean = false
    )

    sealed class Command {
        object FocusUrl : Command()
        object FocusMessage : Command()
        object ConfirmAndFinish : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    var blockedTrackers: String? = null

    private val viewValue: ViewState get() = viewState.value!!

    init {
        viewState.value = ViewState()
    }

    fun setInitialBrokenSite(url: String?, blockedTrackers: String?) {
        onBrokenSiteUrlChanged(url)
        this.blockedTrackers = blockedTrackers
        if (viewValue.url.isNullOrBlank()) {
            command.value = Command.FocusUrl
        } else {
            command.value = Command.FocusMessage
        }
    }

    fun onBrokenSiteUrlChanged(newUrl: String?) {
        viewState.value = viewState.value?.copy(
            url = newUrl,
            submitAllowed = canSubmit(newUrl, viewValue.message)
        )
    }

    fun onFeedbackMessageChanged(newMessage: String?) {
        viewState.value = viewState.value?.copy(
            message = newMessage,
            submitAllowed = canSubmit(viewValue.url, newMessage)
        )
    }

    private fun canSubmit(url: String?, feedbackMessage: String?): Boolean {

        if (feedbackMessage.isNullOrBlank()) {
            return false
        }

        if (url.isNullOrBlank()) {
            return false
        }

        return true
    }

    fun onSubmitPressed(webViewVersion: String) {
        val url = viewValue.url ?: return
        val trackers = blockedTrackers.orEmpty()

        val brokenSite = BrokenSite(
            category = "",
            siteUrl = url,
            upgradeHttps = true,
            blockedTrackers = trackers,
            webViewVersion = webViewVersion,
            siteType = if (Uri.parse(url).isMobileSite) "mobile" else "desktop"
        )

        brokenSiteSender.submitBrokenSiteFeedback(brokenSite)
        pixel.fire(Pixel.PixelName.BROKEN_SITE_REPORTED, mapOf(Pixel.PixelParameter.URL to url))
        command.value = Command.ConfirmAndFinish
    }

    companion object {
        const val UNKNOWN_VERSION = "unknown"
    }
}