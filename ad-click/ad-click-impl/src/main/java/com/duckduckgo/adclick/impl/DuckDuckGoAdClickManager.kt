/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl

import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.adclick.impl.metrics.AdClickCollector
import com.duckduckgo.adclick.impl.pixels.AdClickPixelName
import com.duckduckgo.adclick.impl.pixels.AdClickPixels
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DuckDuckGoAdClickManager @Inject constructor(
    private val adClickData: AdClickData,
    private val adClickAttribution: AdClickAttribution,
    private val adClickPixels: AdClickPixels,
    private val adClickCollector: AdClickCollector,
) : AdClickManager {

    private val publicSuffixDatabase = PublicSuffixDatabase()

    override fun detectAdClick(url: String?, isMainFrame: Boolean) {
        if (url == null) return
        if (!isMainFrame) return

        val adClickResult = adClickAttribution.isAdClick(url)
        if (adClickResult.first) {
            adClicked(adClickResult.second)
            return
        }

        updateExemptions(url)
    }

    override fun setActiveTabId(tabId: String, url: String?, sourceTabId: String?, sourceTabUrl: String?) {
        adClickData.setActiveTab(tabId)
        if (sourceTabId != null && url != null && sourceTabUrl != null) {
            propagateExemption(tabId, url, sourceTabId, sourceTabUrl)
        }
    }

    override fun detectAdDomain(url: String) {
        val urlAdDomainTldPlusOne = toTldPlusOne(url)
        if (urlAdDomainTldPlusOne == null || urlAdDomainTldPlusOne == DUCKDUCKGO_HOST) {
            return
        }

        val savedAdDomainTldPlusOne = adClickData.getAdDomainTldPlusOne()
        addNewExemption(savedAdDomainTldPlusOne, urlAdDomainTldPlusOne, url)

        adClickData.removeAdDomain()
    }

    override fun clearTabId(tabId: String) {
        logcat { "Clear data for tab $tabId." }
        adClickData.remove(tabId)
    }

    override fun clearAll() {
        logcat { "Clear all data." }
        adClickData.removeAll()
    }

    override fun clearAllExpiredAsync() {
        logcat { "Clear all expired entries (asynchronous)." }
        adClickData.removeAllExpired()
    }

    override fun isExemption(documentUrl: String, url: String): Boolean {
        // Example below:
        // documentUrl https://www.onbuy.com/gb/sony-playstation-4-slim-1tb-console-black-new
        // url https://addomain.com/script.js

        // take the domain from the documentUrl
        // if that is in the map, then proceed to check the url
        // take the domain from the url
        // if it's a match, allow it to pass -> return true
        // return false

        val documentUrlHost = UriString.host(documentUrl)?.takeIf { it != DUCKDUCKGO_HOST } ?: return false
        val documentUrlTlDPlusOne = toTldPlusOne(documentUrl) ?: return false

        val hostExempted = adClickData.isHostExempted(documentUrlHost) || adClickData.isHostExempted(documentUrlTlDPlusOne)
        if (!hostExempted) {
            return false
        }

        val expired = adClickData.getExemption()?.isExpired() ?: false
        if (expired) {
            logcat { "isExemption: Url $url is EXPIRED." }
            adClickData.removeExemption()
            return false
        }

        if (adClickAttribution.isAllowed(url)) {
            logcat { "isExemption: Url $url MATCHES the allow list" }
            val exemption = adClickData.getExemption()
            if (adClickData.getCurrentPage().isNotEmpty()) {
                adClickData.setCurrentPage("")
                adClickPixels.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
            }
            val pixelFired = adClickPixels.fireAdClickActivePixel(exemption)
            if (pixelFired && exemption != null) {
                adClickData.addExemption(exemption.copy(adClickActivePixelFired = true))
            }
            return true
        }

        return false
    }

    private fun toTldPlusOne(url: String): String? {
        val urlAdDomain = UriString.host(url)
        if (urlAdDomain.isNullOrEmpty()) return urlAdDomain
        return kotlin.runCatching { publicSuffixDatabase.getEffectiveTldPlusOne(urlAdDomain) }.getOrNull()
    }

    private fun adClicked(detectedAdDomain: String?) {
        val adDomain = detectedAdDomain?.takeIf { adClickAttribution.isDomainDetectionEnabled() }
        if (adDomain != null || adClickAttribution.isHeuristicDetectionEnabled()) {
            val adDomainTldPlusOne = toTldPlusOne(adDomain.orEmpty())
            adClickData.setAdDomainTldPlusOne(adDomainTldPlusOne.orEmpty())
        }
    }

    private fun updateExemptions(url: String) {
        val hostTldPlusOne = toTldPlusOne(url) ?: return
        val exemption = adClickData.getExemption() ?: return

        // clear expiry if not already expired
        if (exemption.isExpired()) {
            adClickData.removeExemption()
        } else {
            if (exemption.hostTldPlusOne != hostTldPlusOne) {
                // navigation to another domain, add expiry
                adClickData.addExemption(
                    exemption.copy(
                        navigationExemptionDeadline = System.currentTimeMillis() + adClickAttribution.getNavigationExpirationMillis(),
                    ),
                )
            } else {
                // navigation back to an existing domain, remove expiry
                adClickData.addExemption(exemption.copy(navigationExemptionDeadline = Exemption.NO_EXPIRY))
            }
        }
    }

    private fun propagateExemption(tabId: String, url: String, sourceTabId: String, sourceTabUrl: String) {
        val hostTldPlusOne = toTldPlusOne(url) ?: return

        val currentExemption = adClickData.getExemption()
        if (currentExemption != null) {
            // Exemptions are propagated when a new tab is opened from another one. If there is already an exemption recorded, it means
            // this is not a new tab that was just opened. No need to do any propagation.
            return
        }

        val sourceTabExemption = adClickData.getExemption(sourceTabId)
        if (sourceTabExemption == null) {
            // This code is specific to the shopping vertical. The behaviour there is uncommon:
            // - ad detection happens on the shopping vertical tab
            // - the advertiser landing page loads in a new tab
            val sourceTabAdDomainTldPLusOne = adClickData.getAdDomainTldPlusOne(sourceTabId)
            addNewExemption(sourceTabAdDomainTldPLusOne, hostTldPlusOne, url)
            adClickData.removeAdDomain(sourceTabId)
            return
        }

        if (sourceTabExemption.hostTldPlusOne == hostTldPlusOne) {
            // check if we don't have already an exemption for the same host (this seems to be a bug)
            val existingExemption = adClickData.getExemption(tabId)
            if (existingExemption?.hostTldPlusOne != hostTldPlusOne) {
                // propagate exemption with no expiry since is the same host as in the source tab and a different
                // one from the existing exemption
                adClickData.addExemption(
                    tabId,
                    sourceTabExemption.copy(
                        navigationExemptionDeadline = Exemption.NO_EXPIRY,
                        adClickActivePixelFired = false,
                    ),
                )
            }
        } else {
            // propagate exemption with timeout since it's a different host
            val sourceTabHostTldPLusOne = toTldPlusOne(sourceTabUrl) ?: return
            adClickData.addExemption(
                tabId,
                sourceTabExemption.copy(
                    hostTldPlusOne = sourceTabHostTldPLusOne,
                    navigationExemptionDeadline = System.currentTimeMillis() + adClickAttribution.getNavigationExpirationMillis(),
                    adClickActivePixelFired = false,
                ),
            )
        }
    }

    private fun addNewExemption(savedAdDomain: String?, urlAdDomain: String, url: String) {
        adClickData.setCurrentPage(url)

        if (savedAdDomain != null) {
            adClickData.addExemption(
                Exemption(
                    hostTldPlusOne = savedAdDomain.ifEmpty { urlAdDomain },
                    navigationExemptionDeadline = Exemption.NO_EXPIRY,
                    exemptionDeadline = System.currentTimeMillis() + adClickAttribution.getTotalExpirationMillis(),
                ),
            )
            adClickCollector.onAdClick()
            adClickPixels.fireAdClickDetectedPixel(
                savedAdDomain = savedAdDomain,
                urlAdDomain = urlAdDomain,
                heuristicEnabled = adClickAttribution.isHeuristicDetectionEnabled(),
                domainEnabled = adClickAttribution.isDomainDetectionEnabled(),
            )
        }
    }

    companion object {
        private const val DUCKDUCKGO_HOST = "duckduckgo.com"
    }
}
