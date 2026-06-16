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

package com.duckduckgo.referral.api

/**
 * Coordinates retrieval of the Play Store install referrer and lets callers await the parsed result.
 * Implementations are store-flavour specific (the Play flavour talks to the Install Referrer library;
 * other flavours no-op).
 */
interface AppInstallationReferrerStateListener {

    /** Starts referrer retrieval. Should be called once, early in app startup. */
    fun initialiseReferralRetrieval()

    /**
     * Returns the parsed referrer, suspending until retrieval completes (or returning immediately once
     * a result, including a cached one, is available). Completion is not guaranteed, so callers should
     * bound the wait — see [MAX_REFERRER_WAIT_TIME_MS].
     */
    suspend fun waitForReferrerCode(): ParsedReferrerResult

    companion object {
        /** Suggested upper bound, in milliseconds, for how long callers should wait on [waitForReferrerCode]. */
        const val MAX_REFERRER_WAIT_TIME_MS = 1_500L
    }
}
