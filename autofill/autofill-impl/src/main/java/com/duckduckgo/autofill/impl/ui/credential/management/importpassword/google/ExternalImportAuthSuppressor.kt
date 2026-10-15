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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

/**
 * Importing from another app moves our app to the background, which would otherwise make the passwords screen
 * ask for device auth again when the user comes back. Activity-scoped so the suppression never leaves the screen that started the import.
 */
interface ExternalImportAuthSuppressor {
    /**
     * Makes the suppression available for as long as [block] runs, and removes it afterwards.
     */
    suspend fun <T> suppressAuthPromptWhileRunning(block: suspend () -> T): T

    /**
     * Checks whether the auth prompt should be suppressed. Any pending suppression is used up by this call, even when it
     * returns false because the import started too long ago, so a second call during the same import always returns false.
     *
     * @return true if an import started by [suppressAuthPromptWhileRunning] is still running and started recently enough
     */
    fun consumeSuppression(): Boolean
}

@SingleInstanceIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealExternalImportAuthSuppressor @Inject constructor(
    private val timeProvider: TimeProvider,
) : ExternalImportAuthSuppressor {

    private var importStartedAt: Long? = null

    override suspend fun <T> suppressAuthPromptWhileRunning(block: suspend () -> T): T {
        importStartedAt = timeProvider.currentTimeMillis()
        return try {
            block()
        } finally {
            importStartedAt = null
        }
    }

    override fun consumeSuppression(): Boolean {
        val startedAt = importStartedAt ?: return false
        importStartedAt = null
        val importDurationMs = timeProvider.currentTimeMillis() - startedAt
        val allowed = importDurationMs <= MAX_IMPORT_DURATION_MS

        logcat(VERBOSE) {
            if (allowed) {
                "Auth prompt suppressed: returning from external import started $importDurationMs ms ago; suppression now consumed"
            } else {
                "Auth prompt not suppressed: external import started $importDurationMs ms ago, over the $MAX_IMPORT_DURATION_MS ms limit"
            }
        }
        return allowed
    }

    companion object {
        private const val MAX_IMPORT_DURATION_MS = 180_000
    }
}
