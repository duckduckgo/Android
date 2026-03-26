/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.referral

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface ReferrerOriginAttributeHandler {
    fun process(referrerParts: List<String>)
}

@ContributesBinding(AppScope::class)
class ReferrerOriginAttributeHandlerImpl @Inject constructor(
    private val appReferrerDataStore: AppReferrerDataStore,
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall,
) : ReferrerOriginAttributeHandler {

    override fun process(referrerParts: List<String>) {
        runCatching {
            logcat(VERBOSE) { "Looking for origin attribute referrer data" }
            var originAttributePart = extractOriginAttribute(referrerParts)

            if (originAttributePart == null && playStoreInstallChecker.installedFromPlayStore()) {
                logcat(VERBOSE) { "No origin attribute referrer data available; assigning one" }
                originAttributePart = DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS
            }

            persistOriginAttribute(originAttributePart)
        }
    }

    private fun extractOriginAttribute(referrerParts: List<String>): String? {
        val originAttributePart = referrerParts.find { it.startsWith("$ORIGIN_ATTRIBUTE_KEY=") }
        if (originAttributePart == null) {
            logcat(VERBOSE) { "Did not find referrer origin attribute key" }
            return null
        }

        logcat(VERBOSE) { "Found referrer origin attribute: $originAttributePart" }

        return originAttributePart.removePrefix("$ORIGIN_ATTRIBUTE_KEY=").also {
            logcat(INFO) { "Found referrer origin attribute value: $it" }
        }
    }

    private fun persistOriginAttribute(originAttributePart: String?) {
        appReferrerDataStore.utmOriginAttributeCampaign = originAttributePart
    }

    companion object {
        const val ORIGIN_ATTRIBUTE_KEY = "origin"
        const val DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS = "funnel_playstore"
    }
}
