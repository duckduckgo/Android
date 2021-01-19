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

package com.duckduckgo.app.referral

import kotlinx.coroutines.delay

class StubAppReferrerFoundStateListener(private val referrer: String, private val mockDelayMs: Long = 0) : AppInstallationReferrerStateListener {
    override suspend fun waitForReferrerCode(): ParsedReferrerResult {
        if (mockDelayMs > 0) delay(mockDelayMs)

        return ParsedReferrerResult.CampaignReferrerFound(referrer)
    }

    override fun initialiseReferralRetrieval() {
    }
}
