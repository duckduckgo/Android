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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.importing.DefaultDomainNameNormalizer
import com.duckduckgo.autofill.impl.importing.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.credentialexchange.api.CredentialExchange
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.credentialexchange.api.ExchangedCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class RealCredentialExchangePasswordImporterTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val urlMatcher = AutofillDomainNameUrlMatcher(UrlUnicodeNormalizerImpl())

    private var alreadySavedUsernames = emptySet<String>()

    private val existingCredentialMatchDetector = object : ExistingCredentialMatchDetector {
        override suspend fun filterExistingCredentials(newCredentials: List<LoginCredentials>) =
            newCredentials.filterNot { it.username in alreadySavedUsernames }
    }

    private val testee = RealCredentialExchangePasswordImporter(
        credentialExchange = mock<CredentialExchange>(),
        domainNameNormalizer = DefaultDomainNameNormalizer(urlMatcher),
        existingCredentialMatchDetector = existingCredentialMatchDetector,
        urlMatcher = urlMatcher,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNoUrlsThenDomainIsNull() = runTest {
        assertNull(importedDomain(emptyList()))
    }

    @Test
    fun whenOneUrlThenItsHostIsTheDomain() = runTest {
        assertEquals("accounts.example.com", importedDomain(listOf("https://accounts.example.com/signin")))
    }

    @Test
    fun whenSeveralUrlsIncludeBaseDomainThenBaseDomainChosen() = runTest {
        val urls = listOf("https://accounts.google.com/signin", "https://mail.google.com", "https://google.com")

        assertEquals("google.com", importedDomain(urls))
    }

    @Test
    fun whenSeveralUrlsHaveNoBaseDomainThenWwwDomainChosen() = runTest {
        val urls = listOf("https://login.example.com", "https://www.example.com")

        assertEquals("www.example.com", importedDomain(urls))
    }

    @Test
    fun whenSeveralUrlsHaveNoBaseOrWwwDomainThenFewestPartsThenAlphabeticalChosen() = runTest {
        val urls = listOf("https://a.b.example.com", "https://z.example.com", "https://m.example.com")

        assertEquals("m.example.com", importedDomain(urls))
    }

    @Test
    fun whenUrlsIncludeBaseAndWwwDomainThenBaseDomainChosen() = runTest {
        val urls = listOf("https://www.example.com", "https://example.com")

        assertEquals("example.com", importedDomain(urls))
    }

    @Test
    fun whenUrlsIncludeAndroidAppThenItIsIgnored() = runTest {
        val urls = listOf("android://abc@com.example.app/", "https://login.example.com")

        assertEquals("login.example.com", importedDomain(urls))
    }

    @Test
    fun whenOnlyUrlAndNotePresentThenCredentialKept() = runTest {
        val result = convert(credential(urls = listOf("https://example.com"), note = "a note"))

        assertEquals(1, result.credentials.size)
        assertEquals("a note", result.credentials.single().notes)
    }

    @Test
    fun whenAllFieldsEmptyThenCredentialDroppedButStillCountedAsSent() = runTest {
        val result = convert(credential(), credential(urls = listOf("https://example.com"), username = "u"))

        assertEquals(1, result.credentials.size)
        assertEquals(2, result.originalCount)
    }

    @Test
    fun whenSameCredentialSentTwiceThenOnlyOneKept() = runTest {
        val login = credential(urls = listOf("https://example.com"), username = "u", password = "p")

        assertEquals(1, convert(login, login).credentials.size)
    }

    @Test
    fun whenCredentialConvertedThenAllFieldsMapped() = runTest {
        val exchanged = ExchangedCredential(
            urls = listOf("https://example.com"),
            title = "Example",
            username = "user",
            password = "pass",
            note = "note",
        )

        val expected = LoginCredentials(domain = "example.com", username = "user", password = "pass", domainTitle = "Example", notes = "note")
        assertEquals(expected, convert(exchanged).credentials.single())
    }

    @Test
    fun whenCredentialAlreadySavedThenDroppedButStillCountedAsSent() = runTest {
        alreadySavedUsernames = setOf("existing")

        val result = convert(
            credential(urls = listOf("https://example.com"), username = "existing"),
            credential(urls = listOf("https://example.com"), username = "new"),
        )

        assertEquals(listOf("new"), result.credentials.map { it.username })
        assertEquals(2, result.originalCount)
    }

    @Test
    fun whenExchangeCancelledThenCancelledReturned() = runTest {
        assertEquals(CredentialExchangeImportResult.Cancelled, testee.convertAndDeduplicate(CredentialExchangeResult.Cancelled))
    }

    @Test
    fun whenExchangeFailedThenFailureReasonPassedThrough() = runTest {
        val result = testee.convertAndDeduplicate(CredentialExchangeResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD))

        assertEquals(CredentialExchangeImportResult.Failure(CredentialExchangeFailure.MALFORMED_PAYLOAD), result)
    }

    private suspend fun importedDomain(urls: List<String>): String? =
        convert(credential(urls = urls, username = "u")).credentials.single().domain

    private suspend fun convert(vararg credentials: ExchangedCredential) =
        testee.convertAndDeduplicate(
            CredentialExchangeResult.Success(credentials.toList(), exporterPackageName = null),
        ) as CredentialExchangeImportResult.Success

    private fun credential(
        urls: List<String> = emptyList(),
        username: String? = null,
        password: String? = null,
        note: String? = null,
    ) = ExchangedCredential(urls = urls, title = null, username = username, password = password, note = note)
}
