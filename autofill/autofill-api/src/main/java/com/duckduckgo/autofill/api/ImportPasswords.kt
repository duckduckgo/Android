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

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

/**
 * Importing passwords from another password manager.
 *
 * The import runs through the FIDO Credential Exchange when the device supports it, and through the Google web flow
 * otherwise or when the exchange fails.
 *
 * Register [importContract] with `registerForActivityResult` in the hosting Activity or Fragment, then start the import
 * with [launchImport]. Every outcome, including the import being unavailable, is delivered to the registered callback.
 */
interface ImportPasswords {

    /** Whether this device can import by either method. */
    suspend fun isSupported(): Boolean

    /**
     * The contract to register for the import's result.
     */
    fun importContract(): ActivityResultContract<ImportPasswordsRequest, ImportPasswordsResult>

    /**
     * Start the import. If it is unsupported, nothing is launched and [ImportPasswordsResult.Unavailable] is delivered to
     * the registered callback.
     *
     * @param params How the import is started.
     * @param launcher The launcher returned when registering [importContract].
     */
    suspend fun launchImport(
        params: ImportPasswordsParams,
        launcher: ActivityResultLauncher<ImportPasswordsRequest>,
    )

    /**
     * Progress of saving the passwords, which continues after [ImportPasswordsResult.Success] is delivered. Replays its most
     * recent value, so a collector that starts after the result still sees the save finish.
     */
    fun importStatus(): Flow<ImportPasswordsStatus>

    /**
     * How the import is started.
     *
     * @param launchSource Where the import is started from.
     */
    data class ImportPasswordsParams(
        val launchSource: AutofillImportLaunchSource,
    ) : Serializable

    /**
     * Input of [importContract]. Only [launchImport] creates it.
     */
    interface ImportPasswordsRequest {
        /**
         * How the import is started, as passed to [launchImport].
         */
        val params: ImportPasswordsParams
    }

    /** Progress of saving the passwords, reported by [importStatus]. */
    sealed interface ImportPasswordsStatus {
        /** The passwords are being saved. */
        data object InProgress : ImportPasswordsStatus

        /** The passwords were saved. */
        data class Finished(
            /** How many passwords were saved. */
            val imported: Int,
            /** How many passwords were not saved, for example because they were already saved. */
            val skipped: Int,
        ) : ImportPasswordsStatus
    }

    /** The outcome of an import, delivered to the callback registered for [importContract]. */
    sealed interface ImportPasswordsResult {
        /** The passwords were received and are being saved; [importStatus] reports when saving finishes. */
        data object Success : ImportPasswordsResult

        /** The user left the import before any passwords were received. */
        data object UserCancelled : ImportPasswordsResult

        sealed interface Error : ImportPasswordsResult {
            /** Trying again may succeed. */
            data object Transient : Error

            /** Trying again will fail the same way. */
            data object Permanent : Error
        }

        /** This device can't import passwords, so nothing was launched. */
        data object Unavailable : ImportPasswordsResult
    }
}
