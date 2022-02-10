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
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory.*
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.isMobileSite
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.TrackingLinkDetector
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider

class BrokenSiteViewModel(
    private val pixel: Pixel,
    private val brokenSiteSender: BrokenSiteSender,
    private val trackingLinkDetector: TrackingLinkDetector
) : ViewModel() {

    data class ViewState(
        val indexSelected: Int = -1,
        val categorySelected: BrokenSiteCategory? = null,
        val submitAllowed: Boolean = false
    )

    sealed class Command {
        object ConfirmAndFinish : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    var indexSelected = -1
    val categories: List<BrokenSiteCategory> = listOf(
        ImagesCategory,
        PaywallCategory,
        CommentsCategory,
        VideosCategory,
        LinksCategory,
        ContentCategory,
        LoginCategory,
        UnsupportedCategory,
        OtherCategory
    )
    private var blockedTrackers: String = ""
    private var surrogates: String = ""
    private var url: String = ""
    private var upgradedHttps: Boolean = false
    private val viewValue: ViewState get() = viewState.value!!

    init {
        viewState.value = ViewState()
    }

    fun setInitialBrokenSite(
        url: String,
        blockedTrackers: String,
        surrogates: String,
        upgradedHttps: Boolean
    ) {
        this.url = url
        this.blockedTrackers = blockedTrackers
        this.upgradedHttps = upgradedHttps
        this.surrogates = surrogates
    }

    fun onCategoryIndexChanged(newIndex: Int) {
        indexSelected = newIndex
    }

    fun onCategorySelectionCancelled() {
        indexSelected = viewState.value?.indexSelected ?: -1
    }

    fun onCategoryAccepted() {
        viewState.value = viewState.value?.copy(
            indexSelected = indexSelected,
            categorySelected = categories.elementAtOrNull(indexSelected),
            submitAllowed = canSubmit()
        )
    }

    fun onSubmitPressed(webViewVersion: String) {
        if (url.isNotEmpty()) {

            val lastTrackingInfo = trackingLinkDetector.lastTrackingLinkInfo

            val brokenSite = if (lastTrackingInfo?.destinationUrl == url) {
                getBrokenSite(lastTrackingInfo.trackingLink, webViewVersion)
            } else {
                getBrokenSite(url, webViewVersion)
            }

            brokenSiteSender.submitBrokenSiteFeedback(brokenSite)
            pixel.fire(
                AppPixelName.BROKEN_SITE_REPORTED,
                mapOf(Pixel.PixelParameter.URL to brokenSite.siteUrl)
            )
        }
        command.value = Command.ConfirmAndFinish
    }

    @VisibleForTesting
    fun getBrokenSite(urlString: String, webViewVersion: String): BrokenSite {
        val category = categories[viewValue.indexSelected]
        return BrokenSite(
            category = category.key,
            siteUrl = urlString,
            upgradeHttps = upgradedHttps,
            blockedTrackers = blockedTrackers,
            surrogates = surrogates,
            webViewVersion = webViewVersion,
            siteType = if (Uri.parse(url).isMobileSite) MOBILE_SITE else DESKTOP_SITE
        )
    }

    private fun canSubmit(): Boolean = categories.elementAtOrNull(indexSelected) != null

    companion object {
        const val WEBVIEW_UNKNOWN_VERSION = "unknown"
        const val MOBILE_SITE = "mobile"
        const val DESKTOP_SITE = "desktop"
    }
}

@ContributesMultibinding(AppScope::class)
class BrokenSiteViewModelFactory @Inject constructor(
    private val pixel: Provider<Pixel>,
    private val brokenSiteSender: Provider<BrokenSiteSender>,
    private val trackingLinkDetector: Provider<TrackingLinkDetector>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BrokenSiteViewModel::class.java) -> (
                    BrokenSiteViewModel(
                        pixel.get(),
                        brokenSiteSender.get(),
                        trackingLinkDetector.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
