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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.importing.wideevents.CredentialImportWideEvent
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealCredentialExchangeImportHandlerTest {

    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter = mock()
    private val credentialImporter: CredentialImporter = mock()
    private val webViewCapabilityChecker: ImportGooglePasswordsCapabilityChecker = mock()
    private val credentialImportWideEvent: CredentialImportWideEvent = mock()

    private val testee = RealCredentialExchangeImportHandler(
        credentialExchangePasswordImporter = credentialExchangePasswordImporter,
        credentialImporter = credentialImporter,
        webViewCapabilityChecker = webViewCapabilityChecker,
        credentialImportWideEvent = credentialImportWideEvent,
    )

    @Test
    fun whenExchangeSucceedsThenCredentialsImportedBeforeWideEventGetsExporter() = runTest {
        val credentials = listOf(LoginCredentials(domain = "example.com", username = "u", password = "p"))
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_SUCCESS))
            .thenReturn(CredentialExchangeImportResult.Success(credentials, originalCount = 3, exporterPackageName = EXPORTER))

        val outcome = testee.handle(EXCHANGE_SUCCESS, SOURCE)

        assertEquals(CredentialExchangeImportOutcome.Imported, outcome)
        inOrder(credentialImporter, credentialImportWideEvent) {
            verify(credentialImporter).import(credentials, 3, SOURCE)
            verify(credentialImportWideEvent).onCredentialExchangeSucceeded(EXPORTER)
        }
    }

    @Test
    fun whenExchangeCancelledThenUserCancelledAndWideEventCancelled() = runTest {
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
            .thenReturn(CredentialExchangeImportResult.Cancelled)

        val outcome = testee.handle(CredentialExchangeResult.Cancelled, SOURCE)

        assertEquals(CredentialExchangeImportOutcome.UserCancelled, outcome)
        verify(credentialImportWideEvent).onCredentialExchangeCancelled()
        verify(credentialImporter, never()).import(any(), any(), any())
    }

    @Test
    fun whenExchangeFailsAndWebViewCanImportThenFallsBackToWebFlowAndNothingImported() = runTest {
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(true)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_FAILURE))
            .thenReturn(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD, EXPORTER))

        val outcome = testee.handle(EXCHANGE_FAILURE, SOURCE)

        assertEquals(CredentialExchangeImportOutcome.FallBackToWebFlow, outcome)
        verify(credentialImportWideEvent).onCredentialExchangeFellBackToWebFlow(CredentialExchangeFailure.MALFORMED_PAYLOAD, EXPORTER)
        verify(credentialImportWideEvent, never()).onCredentialExchangeFailed(any(), anyOrNull())
        verify(credentialImporter, never()).import(any(), any(), any())
    }

    @Test
    fun whenExchangeFailsAndWebViewCannotImportThenFailedAndNoFallback() = runTest {
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(false)
        whenever(credentialExchangePasswordImporter.convertAndDeduplicate(EXCHANGE_FAILURE))
            .thenReturn(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD))

        val outcome = testee.handle(EXCHANGE_FAILURE, SOURCE)

        assertEquals(CredentialExchangeImportOutcome.Failed, outcome)
        verify(credentialImportWideEvent).onCredentialExchangeFailed(CredentialExchangeFailure.MALFORMED_PAYLOAD, null)
        verify(credentialImportWideEvent, never()).onCredentialExchangeFellBackToWebFlow(any(), anyOrNull())
    }

    private companion object {
        val SOURCE = AutofillImportLaunchSource.PasswordManagementEmptyState
        val EXCHANGE_SUCCESS = CredentialExchangeResult.Success(emptyList(), exporterPackageName = null)
        val EXCHANGE_FAILURE = CredentialExchangeResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD)
        const val EXPORTER = "com.example.exporter"
    }
}
