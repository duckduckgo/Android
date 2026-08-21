/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboarding

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

interface OnboardingPrivacyConfigPersistedGate {

    /**
     * Waits for the privacy config to be persisted, which is a signal that bundled privacy config is ready,
     * and reports whether that happened in time.
     *
     * The wait window is opened by the first caller and shared with every other caller, so in case of slow initial config load,
     * the decisions to abort is made once and each caller doesn't add total wait time. Once the window
     * has expired, later callers get an immediate answer.
     *
     * @return true if the privacy config was persisted in time, false otherwise
     */
    suspend fun awaitPersisted(): Boolean
}

@ContributesBinding(AppScope::class, boundType = OnboardingPrivacyConfigPersistedGate::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
@SingleInstanceIn(AppScope::class)
class OnboardingPrivacyConfigPersistedGateImpl @Inject constructor() : OnboardingPrivacyConfigPersistedGate, PrivacyConfigCallbackPlugin {

    private val privacyPersisted = CompletableDeferred<Unit>()
    private val waitLock = Mutex()
    private var waitWindowExpired = false

    override suspend fun awaitPersisted(): Boolean {
        if (privacyPersisted.isCompleted) return true
        return waitLock.withLock {
            when {
                privacyPersisted.isCompleted -> true
                waitWindowExpired -> false
                else -> {
                    val persisted = withTimeoutOrNull(PRIVACY_CONFIG_WAIT_TIMEOUT) { privacyPersisted.await() } != null
                    waitWindowExpired = !persisted
                    persisted
                }
            }
        }
    }

    override fun onPrivacyConfigPersisted() {
        super.onPrivacyConfigPersisted()
        privacyPersisted.complete(Unit)
    }

    override fun onPrivacyConfigDownloaded() = Unit

    companion object {
        private val PRIVACY_CONFIG_WAIT_TIMEOUT = 2.seconds
    }
}
