/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.deviceauth

import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

/**
 * A grace period for autofill authorization.
 * This is used to allow autofill authorization to be skipped for a short period of time after a successful authorization.
 */
interface AutofillAuthorizationGracePeriod {

    /**
     * Can be used to determine if device auth is required. If not required, it can be bypassed.
     * @return true if authorization is required, false otherwise
     */
    fun isAuthRequired(): Boolean

    /**
     * Records the timestamp of a successful device authorization
     */
    fun recordSuccessfulAuthorization()

    /**
     * Requests an extended grace period. This may extend the grace period to a longer duration.
     */
    fun requestExtendedGracePeriod()

    /**
     * Removes the request for an extended grace period
     */
    fun removeRequestForExtendedGracePeriod()

    /**
     * Invalidates the grace period, so that the next call to [isAuthRequired] will return true
     */
    fun invalidate()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutofillTimeBasedAuthorizationGracePeriod @Inject constructor(
    private val timeProvider: TimeProvider,
) : AutofillAuthorizationGracePeriod {

    private var lastSuccessfulAuthTime: Long? = null
    private var extendedGraceTimeRequested: Long? = null

    override fun recordSuccessfulAuthorization() {
        lastSuccessfulAuthTime = timeProvider.currentTimeMillis()
        logcat(VERBOSE) { "Recording timestamp of successful auth" }
    }

    override fun requestExtendedGracePeriod() {
        extendedGraceTimeRequested = timeProvider.currentTimeMillis()
    }

    override fun removeRequestForExtendedGracePeriod() {
        extendedGraceTimeRequested = null
    }

    override fun isAuthRequired(): Boolean {
        lastSuccessfulAuthTime?.let { lastAuthTime ->
            val timeSinceLastAuth = timeProvider.currentTimeMillis() - lastAuthTime
            logcat(VERBOSE) { "Last authentication was $timeSinceLastAuth ms ago" }
            if (timeSinceLastAuth <= AUTH_GRACE_PERIOD_MS) {
                logcat(VERBOSE) { "Within grace period; auth not required" }
                return false
            }
        }

        if (inExtendedGracePeriod()) {
            logcat(VERBOSE) { "Within extended grace period; auth not required" }
            return false
        }

        logcat(VERBOSE) { "No last auth time recorded or outside grace period; auth required" }
        return true
    }

    private fun inExtendedGracePeriod(): Boolean {
        val extendedRequest = extendedGraceTimeRequested
        if (extendedRequest == null) {
            return false
        } else {
            val timeSinceExtendedGrace = timeProvider.currentTimeMillis() - extendedRequest
            return timeSinceExtendedGrace <= AUTH_GRACE_EXTENDED_PERIOD_MS
        }
    }

    override fun invalidate() {
        lastSuccessfulAuthTime = null
        removeRequestForExtendedGracePeriod()
    }

    companion object {
        private const val AUTH_GRACE_PERIOD_MS = 15_000
        private const val AUTH_GRACE_EXTENDED_PERIOD_MS = 180_000
    }
}
