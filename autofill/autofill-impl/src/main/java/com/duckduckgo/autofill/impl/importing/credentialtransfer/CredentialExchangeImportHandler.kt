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

package com.duckduckgo.autofill.impl.importing.credentialtransfer

import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.importing.wideevents.CredentialImportWideEvent
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Saves what a credential exchange returned and records the outcome. A failed exchange falls back to the web
 * flow when this device supports it.
 */
interface CredentialExchangeImportHandler {
    suspend fun handle(result: CredentialExchangeResult, source: AutofillImportLaunchSource): CredentialExchangeImportOutcome
}

sealed interface CredentialExchangeImportOutcome {
    /** The credentials were handed over for saving. */
    data object Imported : CredentialExchangeImportOutcome

    data object UserCancelled : CredentialExchangeImportOutcome

    /** The exchange failed and the web flow can run instead, as part of the same attempt. */
    data object FallBackToWebFlow : CredentialExchangeImportOutcome

    /** The exchange failed and the web flow is not available. */
    data object Failed : CredentialExchangeImportOutcome
}

@ContributesBinding(AppScope::class)
class RealCredentialExchangeImportHandler @Inject constructor(
    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter,
    private val credentialImporter: CredentialImporter,
    private val webViewCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
    private val credentialImportWideEvent: CredentialImportWideEvent,
) : CredentialExchangeImportHandler {

    override suspend fun handle(result: CredentialExchangeResult, source: AutofillImportLaunchSource): CredentialExchangeImportOutcome {
        return when (val converted = credentialExchangePasswordImporter.convertAndDeduplicate(result)) {
            is CredentialExchangeImportResult.Success -> {
                credentialImporter.import(converted.credentials, converted.originalCount, source)
                credentialImportWideEvent.onCredentialExchangeSucceeded(converted.exporterPackageName)
                CredentialExchangeImportOutcome.Imported
            }
            is CredentialExchangeImportResult.Cancelled -> {
                credentialImportWideEvent.onCredentialExchangeCancelled()
                CredentialExchangeImportOutcome.UserCancelled
            }
            is CredentialExchangeImportResult.Failure -> onFailure(converted)
        }
    }

    private suspend fun onFailure(failure: CredentialExchangeImportResult.Failure): CredentialExchangeImportOutcome {
        return if (webViewCapabilityChecker.webViewCapableOfImporting()) {
            logcat(WARN) { "Credential exchange failed (${failure.reason}), falling back to web flow" }
            credentialImportWideEvent.onCredentialExchangeFellBackToWebFlow(failure.reason, failure.exporterPackageName)
            CredentialExchangeImportOutcome.FallBackToWebFlow
        } else {
            logcat(WARN) { "Credential exchange failed (${failure.reason}), web flow not supported" }
            credentialImportWideEvent.onCredentialExchangeFailed(failure.reason, failure.exporterPackageName)
            CredentialExchangeImportOutcome.Failed
        }
    }
}
