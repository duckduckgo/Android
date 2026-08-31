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
     * True when the app link's domain always launches its app, bypassing the usual user-gesture and prompt requirements.
     *
     * @param appLink the app link being evaluated.
     */
    fun isAlwaysTriggerDomain(appLink: AppLink): Boolean
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAppLinksHandler @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : AppLinksHandler {

    var previousUrl: String? = null
    var isAUserQuery = false
    var hasTriggeredForDomain = false

    // Domains exempt from every suppression rule below  as they launch on repeat navigations to the same
    // domain, and without a user gesture.
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

        val urlString = appLink.uriString
        val isAlwaysTriggerDomain = isAlwaysTriggerDomain(appLink)

        // HTTP navigations shouldn't launch apps unless started with a user gesture. That is unless
        // the "trusted-caller" carve-out applies - if an app opens a Custom Tab, App Links that
        // point back to that same app should be allowed even without user interaction.
        if (!isAlwaysTriggerDomain && androidBrowserConfigFeature.customTabEndlessLoopFix().isEnabled()) {
            if (!hasGesture && !isTrustedCaller(appLink, clientPackage)) {
                return false
            }
        }

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
        val targetPackage = appLink.appIntent?.component?.packageName ?: appLink.appIntent?.`package`
        return targetPackage != null && callerPackage == targetPackage
    }

    override fun isAlwaysTriggerDomain(appLink: AppLink): Boolean {
        return alwaysTriggerList.contains(appLink.uriString.extractDomain())
    }
}
