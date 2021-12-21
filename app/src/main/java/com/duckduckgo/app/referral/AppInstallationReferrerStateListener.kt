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

package com.duckduckgo.app.referral

import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface AppInstallationReferrerStateListener {

    fun initialiseReferralRetrieval()
    suspend fun waitForReferrerCode(): ParsedReferrerResult

    companion object {
        const val MAX_REFERRER_WAIT_TIME_MS = 1_500L
    }
}

@SingleInstanceIn(AppScope::class)
class EmptyReferrerStateListener @Inject constructor() :
    AppInstallationReferrerStateListener, AtbInitializerListener {

    private var referralResult: ParsedReferrerResult = ParsedReferrerResult.ReferrerInitialising

    /** Initialises the referrer service. This should only be called once. */
    override fun initialiseReferralRetrieval() {
        Timber.d("Empty referrer, nothing to do here")
        referralResult = ParsedReferrerResult.ReferrerNotFound()
    }

    /**
     * Retrieves the app installation referral code. This might return a result immediately or might
     * wait for a result to become available. There is no guarantee that a result will ever be
     * returned.
     *
     * It is the caller's responsibility to guard against this function not returning a result in a
     * timely manner, or not returning a result ever.
     */
    override suspend fun waitForReferrerCode(): ParsedReferrerResult {
        if (referralResult != ParsedReferrerResult.ReferrerInitialising) {
            Timber.d("Referrer already determined; immediately answering")
            return referralResult
        }

        return referralResult
    }

    override suspend fun beforeAtbInit() {
        waitForReferrerCode()
    }

    override fun beforeAtbInitTimeoutMillis(): Long =
        AppInstallationReferrerStateListener.MAX_REFERRER_WAIT_TIME_MS
}
