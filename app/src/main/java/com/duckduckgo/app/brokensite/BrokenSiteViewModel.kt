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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.ViewState
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory.*
import com.duckduckgo.app.brokensite.model.SiteProtectionsState
import com.duckduckgo.app.brokensite.model.SiteProtectionsState.DISABLED
import com.duckduckgo.app.brokensite.model.SiteProtectionsState.DISABLED_BY_REMOTE_CONFIG
import com.duckduckgo.app.brokensite.model.SiteProtectionsState.ENABLED
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class BrokenSiteViewModel @Inject constructor(
    private val pixel: Pixel,
    private val brokenSiteSender: BrokenSiteSender,
    private val ampLinks: AmpLinks,
    private val featureToggle: FeatureToggle,
    private val contentBlocking: ContentBlocking,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val userAllowListRepository: UserAllowListRepository,
    moshi: Moshi,
) : ViewModel() {
    private val jsonStringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )

    data class ViewState(
        val indexSelected: Int = -1,
        val categorySelected: BrokenSiteCategory? = null,
        var submitAllowed: Boolean = true,
        val protectionsState: SiteProtectionsState? = null,
    )

    sealed class Command {
        object ConfirmAndFinish : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    var indexSelected = -1
    val categories: List<BrokenSiteCategory> = listOf(
        BlockedCategory,
        LayoutCategory,
        EmptySpacesCategory,
        ShoppingCategory,
        PaywallCategory,
        CommentsCategory,
        VideosCategory,
        LoginCategory,
        OtherCategory,
    )
    private var blockedTrackers: String = ""
    private var surrogates: String = ""
    private var url: String = ""
    private var upgradedHttps: Boolean = false
    private val viewValue: ViewState get() = viewState.value!!
    private var urlParametersRemoved: Boolean = false
    private var consentManaged: Boolean = false
    private var consentOptOutFailed: Boolean = false
    private var consentSelfTestFailed: Boolean = false
    private var params: Array<out String> = emptyArray()
    private var errorCodes: Array<out String> = emptyArray()
    private var httpErrorCodes: String = ""
    private var isDesktopMode: Boolean = false

    var shuffledCategories = mutableListOf<BrokenSiteCategory>()

    init {
        shuffledCategories = setCategories(categories)
        viewState.value = ViewState()
    }

    fun setInitialBrokenSite(
        url: String,
        blockedTrackers: String,
        surrogates: String,
        upgradedHttps: Boolean,
        urlParametersRemoved: Boolean,
        consentManaged: Boolean,
        consentOptOutFailed: Boolean,
        consentSelfTestFailed: Boolean,
        params: Array<out String>,
        errorCodes: Array<out String>,
        httpErrorCodes: String,
        isDesktopMode: Boolean,
    ) {
        this.url = url
        this.blockedTrackers = blockedTrackers
        this.upgradedHttps = upgradedHttps
        this.surrogates = surrogates
        this.urlParametersRemoved = urlParametersRemoved
        this.consentManaged = consentManaged
        this.consentOptOutFailed = consentOptOutFailed
        this.consentSelfTestFailed = consentSelfTestFailed
        this.params = params
        this.errorCodes = errorCodes
        this.httpErrorCodes = httpErrorCodes
        this.isDesktopMode = isDesktopMode

        loadProtectionsState()
    }

    private fun setCategories(categoryList: List<BrokenSiteCategory>): MutableList<BrokenSiteCategory> {
        val categories = categoryList.map { it }.toMutableList()
        val shuffledCategories = categories.slice(0..7).shuffled().toMutableList()
        shuffledCategories.add(categories[8])
        return shuffledCategories
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
            categorySelected = shuffledCategories.elementAtOrNull(indexSelected),
        )
    }

    fun onProtectionsToggled(protectionsEnabled: Boolean) {
        val domain = getDomain() ?: return

        viewModelScope.launch {
            if (protectionsEnabled) {
                userAllowListRepository.removeDomainFromUserAllowList(domain)
                pixel.fire(AppPixelName.BROKEN_SITE_ALLOWLIST_REMOVE)
            } else {
                userAllowListRepository.addDomainToUserAllowList(domain)
                pixel.fire(AppPixelName.BROKEN_SITE_ALLOWLIST_ADD)
            }
        }
    }

    fun onSubmitPressed(webViewVersion: String, description: String?, loginSite: String?) {
        viewState.value?.submitAllowed = false
        if (url.isNotEmpty()) {
            val lastAmpLinkInfo = ampLinks.lastAmpLinkInfo

            val loginSiteFinal = if (shuffledCategories.elementAtOrNull(viewValue.indexSelected)?.key == BrokenSiteCategory.LOGIN_CATEGORY_KEY) {
                loginSite
            } else {
                ""
            }

            val brokenSite = if (lastAmpLinkInfo?.destinationUrl == url) {
                getBrokenSite(lastAmpLinkInfo.ampLink, webViewVersion, description, loginSiteFinal)
            } else {
                getBrokenSite(url, webViewVersion, description, loginSiteFinal)
            }

            brokenSiteSender.submitBrokenSiteFeedback(brokenSite)

            val pixelParams = mutableMapOf(
                Pixel.PixelParameter.URL to brokenSite.siteUrl,
            )

            // add any additional params - for example, from the privacy dashboard
            params.forEach {
                pixelParams[it] = true.toString()
            }

            pixel.fire(
                AppPixelName.BROKEN_SITE_REPORTED,
                pixelParams,
            )
        }
        command.value = Command.ConfirmAndFinish
    }

    private fun loadProtectionsState() {
        val domain = getDomain() ?: return

        if (
            !featureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value) ||
            contentBlocking.isAnException(url) ||
            unprotectedTemporary.isAnException(url)
        ) {
            viewState.setProtectionsState(DISABLED_BY_REMOTE_CONFIG)
            return
        }

        userAllowListRepository
            .domainsInUserAllowListFlow()
            .map { allowListedDomains -> if (domain in allowListedDomains) DISABLED else ENABLED }
            .distinctUntilChanged()
            .onEach { protectionsState -> viewState.setProtectionsState(protectionsState) }
            .launchIn(viewModelScope)
    }

    private fun getDomain(): String? =
        try {
            url.takeUnless { it.isEmpty() }?.extractDomain()
        } catch (e: IllegalArgumentException) {
            null
        }

    @VisibleForTesting
    fun getBrokenSite(
        urlString: String,
        webViewVersion: String,
        description: String?,
        loginSite: String?,
    ): BrokenSite {
        val category = shuffledCategories.elementAtOrNull(viewValue.indexSelected)
        return BrokenSite(
            category = category?.key,
            description = description,
            siteUrl = urlString,
            upgradeHttps = upgradedHttps,
            blockedTrackers = blockedTrackers,
            surrogates = surrogates,
            webViewVersion = webViewVersion,
            siteType = if (isDesktopMode) DESKTOP_SITE else MOBILE_SITE,
            urlParametersRemoved = urlParametersRemoved,
            consentManaged = consentManaged,
            consentOptOutFailed = consentOptOutFailed,
            consentSelfTestFailed = consentSelfTestFailed,
            errorCodes = jsonStringListAdapter.toJson(errorCodes.toList()).toString(),
            httpErrorCodes = httpErrorCodes,
            loginSite = loginSite,
        )
    }

    companion object {
        const val MOBILE_SITE = "mobile"
        const val DESKTOP_SITE = "desktop"
    }
}

private fun MutableLiveData<ViewState>.setProtectionsState(state: SiteProtectionsState?) {
    value = value!!.copy(protectionsState = state)
}
