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

package com.duckduckgo.autofill.impl.ui.credential.saving.declines

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
* Repeated prompts to use Autofill (e.g., save login credentials) might annoy a user who doesn't want to use Autofill.
* If the user has declined too many times without using it, we will prompt them to disable.
*
* This class is used to track the number of times a user has declined to use Autofill when prompted.
* It should be permanently disabled, by calling disableDeclineCounter(), when user:
*    - saves a credential, or
*    - chooses to disable autofill when prompted to disable autofill, or
*    - chooses to keep using autofill when prompted to disable autofill
*/
interface AutofillDeclineCounter {

    /**
     * Should be called every time a user declines to save credentials
     * @param domain the domain of the site the user declined to save credentials for
     */
    suspend fun userDeclinedToSaveCredentials(domain: String?)

    /**
     * Determine if the user should be prompted to disable autofill
     * @return true if the user should be prompted to disable autofill
     */
    suspend fun shouldPromptToDisableAutofill(): Boolean

    /**
     * Permanently disable the autofill decline counter
     */
    suspend fun disableDeclineCounter()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AutofillDisablingDeclineCounter @Inject constructor(
    private val autofillStore: AutofillStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : AutofillDeclineCounter {

    @VisibleForTesting
    var isActive = false

    /**
     * The previous domain for which we have recorded a decline, held in-memory only.
     */
    @VisibleForTesting
    var currentSessionPreviousDeclinedDomain: String? = null

    init {
        appCoroutineScope.launch(dispatchers.io()) {
            isActive = determineIfDeclineCounterIsActive().also {
                Timber.v("Determined if decline counter should be active: $it")
            }
        }
    }

    override suspend fun userDeclinedToSaveCredentials(domain: String?) {
        Timber.v("User declined to save credentials for %s. isActive: %s", domain, isActive)
        if (!isActive || domain == null) return

        withContext(dispatchers.io()) {
            if (shouldRecordDecline(domain)) {
                recordDeclineForDomain(domain)
            }
        }
    }

    private fun recordDeclineForDomain(domain: String) {
        Timber.d("User declined to save credentials for a new domain; recording the decline")
        autofillStore.autofillDeclineCount++
        currentSessionPreviousDeclinedDomain = domain
    }

    private fun shouldRecordDecline(domain: String) = domain != currentSessionPreviousDeclinedDomain

    override suspend fun disableDeclineCounter() {
        isActive = false
        currentSessionPreviousDeclinedDomain = null

        withContext(dispatchers.io()) {
            autofillStore.monitorDeclineCounts = false
        }
    }

    override suspend fun shouldPromptToDisableAutofill(): Boolean {
        if (!isActive) return false

        return withContext(dispatchers.io()) {
            val shouldOffer = autofillStore.autofillDeclineCount >= GLOBAL_DECLINE_COUNT_THRESHOLD

            Timber.v(
                "User declined to save credentials %d times globally from all sessions. Should prompt to disable: %s",
                autofillStore.autofillDeclineCount,
                shouldOffer,
            )

            return@withContext shouldOffer
        }
    }

    private suspend fun determineIfDeclineCounterIsActive(): Boolean {
        return withContext(dispatchers.io()) {
            autofillStore.autofillEnabled && autofillStore.monitorDeclineCounts && autofillStore.autofillAvailable
        }
    }

    companion object {
        private const val GLOBAL_DECLINE_COUNT_THRESHOLD = 2
    }
}
