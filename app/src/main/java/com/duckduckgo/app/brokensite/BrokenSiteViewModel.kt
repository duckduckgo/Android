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
import com.duckduckgo.app.global.absoluteString
import com.duckduckgo.app.global.isMobileSite
import com.duckduckgo.app.statistics.pixels.Pixel

class BrokenSiteViewModel(private val pixel: Pixel, private val brokenSiteSender: BrokenSiteSender) : ViewModel() {

    data class ViewState(
        val indexSelected: Int = -1,
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
    var url: String? = null
    var upgradedHttps: Boolean = false

    private val viewValue: ViewState get() = viewState.value!!

    init {
        viewState.value = ViewState()
    }

    fun setInitialBrokenSite(url: String?, blockedTrackers: String?, upgradedHttps: Boolean) {
        this.url = url
        this.blockedTrackers = blockedTrackers
        this.upgradedHttps = upgradedHttps
    }

    fun onCategoryIndexChanged(newIndex: Int) {
        viewState.value = viewState.value?.copy(
            indexSelected = newIndex,
            submitAllowed = canSubmit(newIndex)
        )
    }

    fun indexSelected(): Int = viewValue.indexSelected

    private fun canSubmit(indexSelected: Int): Boolean = indexSelected > -1

    fun onSubmitPressed(webViewVersion: String, category: String) {

        url?.let {
            val trackers = blockedTrackers.orEmpty()

            val brokenSite = BrokenSite(
                category = category,
                siteUrl = Uri.parse(it).absoluteString,
                upgradeHttps = upgradedHttps,
                blockedTrackers = trackers,
                webViewVersion = webViewVersion,
                siteType = if (Uri.parse(it).isMobileSite) MOBILE else DESKTOP
            )

            brokenSiteSender.submitBrokenSiteFeedback(brokenSite)
            //pixel.fire(Pixel.PixelName.BROKEN_SITE_REPORTED, mapOf(Pixel.PixelParameter.URL to it))
        }
        command.value = Command.ConfirmAndFinish
    }

    companion object {
        const val UNKNOWN_VERSION = "unknown"
        const val MOBILE = "mobile"
        const val DESKTOP = "desktop"
    }
}