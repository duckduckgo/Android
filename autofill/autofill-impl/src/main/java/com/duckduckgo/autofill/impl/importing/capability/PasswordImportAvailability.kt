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

package com.duckduckgo.autofill.impl.importing.capability

import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Whether we have at least one way to import passwords:
 * either from the FIDO OS credential exchange, or the Google web flow (which needs WebView support).
 */
interface PasswordImportAvailability {
    suspend fun canImport(): Boolean
}

@ContributesBinding(AppScope::class)
class RealPasswordImportAvailability @Inject constructor(
    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter,
    private val webViewCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
) : PasswordImportAvailability {

    override suspend fun canImport(): Boolean =
        credentialExchangePasswordImporter.isSupported() || webViewCapabilityChecker.webViewCapableOfImporting()
}
