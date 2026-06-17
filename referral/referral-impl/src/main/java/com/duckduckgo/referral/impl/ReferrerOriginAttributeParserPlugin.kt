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

package com.duckduckgo.referral.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.referral.api.ReferrerParserPlugin
import com.duckduckgo.verifiedinstallation.installsource.VerificationCheckPlayStoreInstall
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ReferrerOriginAttributeParserPlugin @Inject constructor(
    private val appReferrerDataStore: AppReferrerDataStore,
    private val playStoreInstallChecker: VerificationCheckPlayStoreInstall,
) : ReferrerParserPlugin {

    override fun process(referrerParams: Map<String, String>) {
        runCatching {
            logcat(VERBOSE) { "Looking for origin attribute referrer data" }
            var origin = referrerParams[ORIGIN_ATTRIBUTE_KEY]

            if (origin == null && playStoreInstallChecker.installedFromPlayStore()) {
                logcat(VERBOSE) { "No origin attribute referrer data available; assigning one" }
                origin = DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS
            }

            logcat(INFO) { "Persisting referrer origin attribute value: $origin" }
            appReferrerDataStore.utmOriginAttributeCampaign = origin
        }
    }

    companion object {
        const val ORIGIN_ATTRIBUTE_KEY = "origin"
        const val DEFAULT_ATTRIBUTION_FOR_PLAY_STORE_INSTALLS = "funnel_playstore"
    }
}
