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

package com.duckduckgo.referral

import com.duckduckgo.app.referral.*
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.app.referral.ParsedReferrerResult.*
import com.duckduckgo.app.statistics.AtbInitializerListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FDroidReferrerStateListener @Inject constructor() : AppInstallationReferrerStateListener, AtbInitializerListener {

    private var referralResult: ParsedReferrerResult = ReferrerInitialising

    /**
     * Initialises the referrer service. This should only be called once.
     */
    override fun initialiseReferralRetrieval() {
        Timber.d("Referrer from F-Droid, nothing to do here")
        referralResult = ReferrerNotFound()
    }

    /**
     * Retrieves the app installation referral code.
     * This might return a result immediately or might wait for a result to become available. There is no guarantee that a result will ever be returned.
     *
     * It is the caller's responsibility to guard against this function not returning a result in a timely manner, or not returning a result ever.
     */
    override suspend fun waitForReferrerCode(): ParsedReferrerResult {
        if (referralResult != ReferrerInitialising) {
            Timber.d("Referrer already determined; immediately answering")
            return referralResult
        }

        return referralResult
    }

    override suspend fun beforeAtbInit() {
        waitForReferrerCode()
    }

    override fun beforeAtbInitTimeoutMillis(): Long = MAX_REFERRER_WAIT_TIME_MS
}
