/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.applinks

import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AppLinksHandler {
    fun handleAppLink(
        isForMainFrame: Boolean,
        appLink: AppLink,
        hasGesture: Boolean,
        clientPackage: String?,
        appLinksEnabled: Boolean,
        shouldHaltWebNavigation: Boolean,
        launchAppLink: () -> Unit,
    ): Boolean

    fun updatePreviousUrl(urlString: String?)
    fun setUserQueryState(state: Boolean)
    fun isUserQuery(): Boolean

    /**
     * True when the app link resolves back to the app that opened the custom tab
     *
     * @param appLink the app link being evaluated.
     * @param callerPackage package that launched the custom tab (if any).
     */
    fun isTrustedCaller(appLink: AppLink, callerPackage: String?): Boolean

    /**
     * Records the page that navigations start from, used by [isHandOff].
     *
     * @param url the url of the current page, or null when no page is shown.
     * @param appLink the app link for [url] when an app can open it, or null otherwise.
     */
    fun updateCurrentPage(url: String?, appLink: AppLink?)

    /**
     * True when the app link sends the user from the current page to an app that can't open that page, on the same site
     * (e.g. digid.nl -> app.digid.nl). Hand-offs launch on every attempt, halt the web navigation, and open without a
     * prompt in custom tabs. They still need a user gesture. While [AppLinksHandOffFeature] is disabled, only the built-in
     * always-trigger domains count.
     *
     * @param appLink the app link being evaluated.
     */
    fun isHandOff(appLink: AppLink): Boolean
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAppLinksHandler @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val appLinksHandOffFeature: AppLinksHandOffFeature,
) : AppLinksHandler {

    var previousUrl: String? = null
    var isAUserQuery = false
    var hasTriggeredForDomain = false
    private var currentPageUrl: String? = null
    private var currentPageAppPackage: String? = null

    // Used instead of hand-off detection while it's disabled. Their sites hand off to these App Link domains from the same
    // domain on every attempt (digid.nl -> app.digid.nl), and the App Link page itself isn't meant to load in the browser.
    private val alwaysTriggerList = listOf("app.digid.nl")

    override fun handleAppLink(
        isForMainFrame: Boolean,
        appLink: AppLink,
        hasGesture: Boolean,
        clientPackage: String?,
        appLinksEnabled: Boolean,
        shouldHaltWebNavigation: Boolean,
        launchAppLink: () -> Unit,
    ): Boolean {
        if (!appLinksEnabled || !isForMainFrame) {
            return false
        }

        // HTTP navigations shouldn't launch apps unless started with a user gesture. That is unless
        // the "trusted-caller" carve-out applies - if an app opens a Custom Tab, App Links that
        // point back to that same app should be allowed even without user interaction.
        if (androidBrowserConfigFeature.customTabEndlessLoopFix().isEnabled()) {
            if (!hasGesture && !isTrustedCaller(appLink, clientPackage)) {
                return false
            }
        }

        val urlString = appLink.uriString
        val detectsHandOffs = appLinksHandOffFeature.self().isEnabled()

        // Like Chrome's same-host rule: moving between pages one app can open is browsing, so the once-per-domain rule
        // applies, but a link to an app that can't open the current page is a hand-off with no useful web page behind it.
        if (detectsHandOffs && isHandOff(appLink)) {
            previousUrl = urlString
            launchAppLink()
            hasTriggeredForDomain = true
            return true
        }

        val isAlwaysTriggerDomain = !detectsHandOffs && isAlwaysTriggerDomain(appLink)
        previousUrl?.let {
            if (isSameOrSubdomain(it, urlString)) {
                if (isAUserQuery || !hasTriggeredForDomain || isAlwaysTriggerDomain) {
                    previousUrl = urlString
                    launchAppLink()
                    hasTriggeredForDomain = true
                    if (isAlwaysTriggerDomain) return true
                }
                return false
            }
        }

        previousUrl = urlString
        launchAppLink()
        hasTriggeredForDomain = true
        return shouldHaltWebNavigation
    }

    private fun isSameOrSubdomain(
        previousUrlString: String,
        currentUrlString: String,
    ) = UriString.sameOrSubdomain(previousUrlString, currentUrlString) || UriString.sameOrSubdomain(currentUrlString, previousUrlString)

    override fun updatePreviousUrl(urlString: String?) {
        if (urlString == null || previousUrl?.let { isSameOrSubdomain(it, urlString) } == false) {
            hasTriggeredForDomain = false
        }
        previousUrl = urlString
    }

    override fun setUserQueryState(state: Boolean) {
        isAUserQuery = state
    }

    override fun isUserQuery(): Boolean {
        return isAUserQuery
    }

    override fun isTrustedCaller(appLink: AppLink, callerPackage: String?): Boolean {
        val targetPackage = appLink.targetPackage()
        return targetPackage != null && callerPackage == targetPackage
    }

    override fun updateCurrentPage(url: String?, appLink: AppLink?) {
        currentPageUrl = url
        currentPageAppPackage = appLink?.targetPackage()
    }

    override fun isHandOff(appLink: AppLink): Boolean {
        if (!appLinksHandOffFeature.self().isEnabled()) return isAlwaysTriggerDomain(appLink)

        val pageUrl = currentPageUrl ?: return false
        val targetPackage = appLink.targetPackage() ?: return false
        return targetPackage != currentPageAppPackage && isSameOrSubdomain(pageUrl, appLink.uriString)
    }

    private fun isAlwaysTriggerDomain(appLink: AppLink): Boolean = alwaysTriggerList.contains(appLink.uriString.extractDomain())

    private fun AppLink.targetPackage(): String? = appIntent?.component?.packageName ?: appIntent?.`package`
}
