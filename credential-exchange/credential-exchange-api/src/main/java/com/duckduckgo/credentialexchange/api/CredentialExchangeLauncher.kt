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

package com.duckduckgo.credentialexchange.api

/**
 * Reads credentials out of another app on this device using Android's credential transfer flow,
 * which carries a FIDO Credential Exchange Format (CXF) v1.0 payload.
 *
 * See https://developer.android.com/identity/sign-in/credential-transfer
 * and https://fidoalliance.org/specs/cx/cxf-v1.0-ps-20250814.html
 *
 * This is a reader only. It does not save anything, compare against what is already stored, or show
 * any screen of its own; the system and the exporting app draw the whole selection flow.
 *
 * Only `basic-auth` credentials (a username and a password) are read. Passkeys, payment cards and
 * other CXF types are ignored.
 *
 * Available in `ActivityScope` (and so `FragmentScope`) only: the system flow must be started from the
 * hosting Activity, which is injected. Do not hold it from anything that outlives that Activity, such
 * as a ViewModel, or the flow will be started from a destroyed Activity.
 */
interface CredentialExchangeLauncher {

    /**
     * Starts the system's flow to allow users to exchange passwords into our app.
     *
     * Callers should check [CredentialExchange.isImportSupported] before deciding to show UI offering
     * this. Calling this on a build with no support returns [CredentialExchangeFailure.NOT_SUPPORTED].
     */
    suspend fun launchImportFlow(): CredentialExchangeResult
}
