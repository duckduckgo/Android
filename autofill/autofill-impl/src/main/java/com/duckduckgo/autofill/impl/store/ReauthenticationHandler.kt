/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.store

import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface ReauthenticationHandler {
    fun storeForReauthentication(url: String, password: String? = null)

    fun retrieveReauthData(url: String): ReAuthenticationDetails

    fun clearAll()
}

@ContributesBinding(AppScope::class)
class InMemoryReauthenticationHandler @Inject constructor(private val urlMatcher: AutofillUrlMatcher) : ReauthenticationHandler {

    private val reauthDataByEtldPlus1 = ConcurrentHashMap<String, ReAuthenticationDetails>()

    override fun storeForReauthentication(
        url: String,
        password: String?,
    ) {
        val eTldPlus1 = urlMatcher.extractUrlPartsForAutofill(url).eTldPlus1 ?: return
        if (eTldPlus1 != PERMITTED_E_TLD_PLUS_1) {
            logcat(WARN) { "Ignoring request to store re-auth password for $eTldPlus1" }
            return
        }

        reauthDataByEtldPlus1[eTldPlus1] = ReAuthenticationDetails(password = password).also {
            logcat { "Stored re-auth password for $eTldPlus1" }
        }
    }

    override fun retrieveReauthData(url: String): ReAuthenticationDetails {
        val urlParts = urlMatcher.extractUrlPartsForAutofill(url)
        val eTldPlus1 = urlParts.eTldPlus1 ?: return noAuthenticationDetails

        return reauthDataByEtldPlus1[eTldPlus1] ?: noAuthenticationDetails
    }

    override fun clearAll() {
        reauthDataByEtldPlus1.clear()
        logcat { "Cleared all re-authentication data" }
    }

    companion object {
        private val noAuthenticationDetails = ReAuthenticationDetails(password = null)
        private const val PERMITTED_E_TLD_PLUS_1 = "google.com"
    }
}
