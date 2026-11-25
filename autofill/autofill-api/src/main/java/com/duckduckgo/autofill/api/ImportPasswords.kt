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

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Importing passwords from Google Password Manager.
 *
 * The flow imports through the FIDO Credential Exchange when the device supports it, and through the Google web flow
 * otherwise or when the exchange fails.
 */
interface ImportPasswords {

    /** Whether this device can import by either method. */
    suspend fun isSupported(): Boolean

    /**
     * Start the password import flow.
     *
     * The returned flow may emit [ImportPasswordsEvent.InProgress] while the passwords are being saved, then emits this
     * import's outcome once and completes. It is backed by app-wide state, so collecting
     * it from a scope that survives configuration changes, such as a ViewModel's, keeps the outcome across them. Nothing is
     * replayed: an outcome emitted while nobody collects is lost, as it is on process death. If the flow is unsupported,
     * nothing is started and the returned flow emits [ImportPasswordsEvent.Error.Permanent].
     *
     * @param context Used to start the flow's screens; pass an Activity.
     * @param launchSource Where the flow is started from.
     */
    suspend fun launchImport(
        context: Context,
        launchSource: AutofillImportLaunchSource,
    ): Flow<ImportPasswordsEvent>

    sealed interface ImportPasswordsEvent {
        /** The passwords were received and are being saved. */
        data object InProgress : ImportPasswordsEvent

        /** The passwords were saved. */
        data class Finished(
            val imported: Int,
            val skipped: Int,
        ) : ImportPasswordsEvent

        data object UserCancelled : ImportPasswordsEvent

        sealed interface Error : ImportPasswordsEvent {
            /** Trying again may succeed. */
            data object Transient : Error

            /** Trying again will fail the same way. */
            data object Permanent : Error
        }
    }
}
