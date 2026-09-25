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

package com.duckduckgo.credentialexchange.impl

import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.providerevents.ProviderEventsManager
import androidx.credentials.providerevents.exception.ImportCredentialsCancellationException
import androidx.credentials.providerevents.exception.ImportCredentialsInvalidJsonException
import androidx.credentials.providerevents.exception.ImportCredentialsNoExportOptionException
import androidx.credentials.providerevents.exception.ImportCredentialsUnknownErrorException
import androidx.credentials.providerevents.transfer.ImportCredentialsResponse
import androidx.credentials.providerevents.transfer.ProviderImportCredentialsResponse
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.credentialexchange.api.ExchangedCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealCredentialExchangeLauncherTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val activity: AppCompatActivity = mock()
    private val providerEventsManager: ProviderEventsManager = mock()
    private val providerEventsManagerFactory: ProviderEventsManagerFactory = mock {
        on { create() } doReturn providerEventsManager
    }
    private val cxfPayloadParser: CxfPayloadParser = mock()

    private val testee = RealCredentialExchangeLauncher(
        activity = activity,
        providerEventsManagerFactory = providerEventsManagerFactory,
        cxfPayloadParser = cxfPayloadParser,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenLaunchedThenFlowIsStartedFromTheInjectedActivity() = runTest {
        givenImportThrows(ImportCredentialsCancellationException())

        testee.launchImportFlow()

        verify(providerEventsManager).importCredentials(same(activity), any())
    }

    @Test
    fun whenExporterThrowsCancellationThenResultIsCancelled() = runTest {
        givenImportThrows(ImportCredentialsCancellationException())

        assertTrue(testee.launchImportFlow() is CredentialExchangeResult.Cancelled)
    }

    @Test
    fun whenExporterThrowsUnknownErrorWithNoMessageThenResultIsCancelled() = runTest {
        givenImportThrows(ImportCredentialsUnknownErrorException())

        assertTrue(testee.launchImportFlow() is CredentialExchangeResult.Cancelled)
    }

    @Test
    fun whenExporterThrowsUnknownErrorWithMessageThenResultIsUnknownFailure() = runTest {
        givenImportThrows(ImportCredentialsUnknownErrorException("something broke"))

        assertEquals(CredentialExchangeFailure.UNKNOWN, failure().reason)
    }

    @Test
    fun whenNoExporterAvailableThenResultSaysSo() = runTest {
        givenImportThrows(ImportCredentialsNoExportOptionException())

        assertEquals(CredentialExchangeFailure.NO_EXPORTER_AVAILABLE, failure().reason)
    }

    @Test
    fun whenExporterReturnsInvalidJsonThenResultIsMalformed() = runTest {
        givenImportThrows(ImportCredentialsInvalidJsonException("bad"))

        assertEquals(CredentialExchangeFailure.MALFORMED_PAYLOAD, failure().reason)
    }

    @Test
    fun whenPayloadCannotBeParsedThenResultIsMalformedAndNamesTheExporter() = runTest {
        givenImportReturns("{}")
        whenever(cxfPayloadParser.parse(any())) doReturn CxfParseResult.Malformed

        assertEquals(CredentialExchangeResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD, EXPORTER), failure())
    }

    @Test
    fun whenPayloadIsParsedThenEverythingTheExporterSentIsReturned() = runTest {
        val parsed = listOf(credential("a"), credential("b"))
        givenImportReturns("{\"payload\":1}")
        whenever(cxfPayloadParser.parse(eq("{\"payload\":1}"))) doReturn CxfParseResult.Parsed(parsed)

        assertEquals(CredentialExchangeResult.Success(parsed, EXPORTER), testee.launchImportFlow())
    }

    private suspend fun failure() = testee.launchImportFlow() as CredentialExchangeResult.Failure

    private suspend fun givenImportThrows(exception: Throwable) {
        // thenThrow rejects these: the suspend signature declares no checked exceptions
        whenever(providerEventsManager.importCredentials(any(), any())).thenAnswer { throw exception }
    }

    private suspend fun givenImportReturns(json: String) {
        val callingAppInfo: CallingAppInfo = mock { on { packageName } doReturn EXPORTER }
        val response = ProviderImportCredentialsResponse(ImportCredentialsResponse(json), callingAppInfo)
        whenever(providerEventsManager.importCredentials(any(), any())) doReturn response
    }

    private fun credential(username: String) = ExchangedCredential(
        url = "https://example.com/",
        title = "Example",
        username = username,
        password = "password",
        note = null,
    )

    private companion object {
        const val EXPORTER = "com.example.exporter"
    }
}
