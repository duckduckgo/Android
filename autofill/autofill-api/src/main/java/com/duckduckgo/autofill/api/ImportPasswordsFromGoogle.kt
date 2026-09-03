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

package com.duckduckgo.autofill.api

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * Importing passwords from Google Password Manager.
 *
 * The web flow itself is a screen, not a method here: start
 * [AutofillScreens.AutofillImportPasswordsScreen] for result and read what comes back with
 * [parseResult].
 */
interface ImportPasswordsFromGoogle {

    /** Whether this device can run the import web flow at all. */
    suspend fun isSupported(): Boolean

    /** Reads the outcome out of the intent the import screen returns. */
    fun parseResult(data: Intent?): ImportPasswordsResult

    /**
     * Progress of the import itself, which continues after the web flow returns [ImportPasswordsResult.Success].
     * Replays its most recent value, so a collector that starts late still sees a finished import.
     */
    fun importStatus(): Flow<ImportPasswordsStatus>

    sealed interface ImportPasswordsResult {
        data object Success : ImportPasswordsResult

        data object UserCancelled : ImportPasswordsResult

        sealed interface Error : ImportPasswordsResult {
            data object Transient : Error
            data object Permanent : Error
        }
    }

    sealed interface ImportPasswordsStatus {
        data object InProgress : ImportPasswordsStatus
        data class Finished(
            val imported: Int,
            val skipped: Int,
        ) : ImportPasswordsStatus
    }
}
