/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InMemoryReauthenticationHandlerTest {

    private lateinit var testee: InMemoryReauthenticationHandler
    private val urlMatcher: AutofillUrlMatcher = mock()

    @Before
    fun setUp() {
        testee = InMemoryReauthenticationHandler(urlMatcher)
    }

    @Test
    fun whenStoreForReauthenticationWithValidUrlThenPasswordIsStored() {
        val url = "https://accounts.google.com/signin"
        val password = "testPassword123"
        val eTldPlus1 = "google.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        testee.storeForReauthentication(url, password)

        val result = testee.retrieveReauthData(url)
        assertEquals(password, result.password)
    }

    @Test
    fun whenStoreForReauthenticationWithNullPasswordThenNullIsStored() {
        val url = "https://accounts.google.com/signin"
        val eTldPlus1 = "google.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        testee.storeForReauthentication(url, null)

        val result = testee.retrieveReauthData(url)
        assertNull(result.password)
    }

    @Test
    fun whenStoreForReauthenticationWithInvalidUrlThenNothingIsStored() {
        val url = "invalid-url"
        val password = "testPassword123"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = null, userFacingETldPlus1 = null, subdomain = null),
        )

        testee.storeForReauthentication(url, password)

        val result = testee.retrieveReauthData(url)
        assertNull(result.password)
    }

    @Test
    fun whenRetrieveReauthDataForUnknownUrlThenReturnsEmptyDetails() {
        val url = "https://unknown.com/login"
        val eTldPlus1 = "unknown.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        val result = testee.retrieveReauthData(url)

        assertNull(result.password)
    }

    @Test
    fun whenRetrieveReauthDataWithInvalidUrlThenReturnsEmptyDetails() {
        val url = "invalid-url"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = null, userFacingETldPlus1 = null, subdomain = null),
        )

        val result = testee.retrieveReauthData(url)

        assertNull(result.password)
    }

    @Test
    fun whenMultipleUrlsWithSameETldPlus1ThenSamePasswordIsRetrieved() {
        val url1 = "https://accounts.google.com/signin"
        val url2 = "https://passwords.google.com/export"
        val password = "testPassword123"
        val eTldPlus1 = "google.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(url1)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )
        whenever(urlMatcher.extractUrlPartsForAutofill(url2)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        testee.storeForReauthentication(url1, password)

        val result = testee.retrieveReauthData(url2)
        assertEquals(password, result.password)
    }

    @Test
    fun whenDifferentETldPlus1ThenOnlyGooglePasswordIsStored() {
        val googleUrl = "https://accounts.google.com/signin"
        val microsoftUrl = "https://login.microsoftonline.com/signin"
        val googlePassword = "googlePassword123"
        val microsoftPassword = "microsoftPassword456"

        whenever(urlMatcher.extractUrlPartsForAutofill(googleUrl)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = "google.com", userFacingETldPlus1 = "google.com", subdomain = null),
        )
        whenever(urlMatcher.extractUrlPartsForAutofill(microsoftUrl)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = "microsoftonline.com", userFacingETldPlus1 = "microsoftonline.com", subdomain = null),
        )

        testee.storeForReauthentication(googleUrl, googlePassword)
        testee.storeForReauthentication(microsoftUrl, microsoftPassword)

        val googleResult = testee.retrieveReauthData(googleUrl)
        val microsoftResult = testee.retrieveReauthData(microsoftUrl)

        // Only Google password should be stored due to eTLD+1 guard
        assertEquals(googlePassword, googleResult.password)
        assertNull(microsoftResult.password)
    }

    @Test
    fun whenOverwritingExistingPasswordThenNewPasswordIsRetrieved() {
        val url = "https://accounts.google.com/signin"
        val oldPassword = "oldPassword123"
        val newPassword = "newPassword456"
        val eTldPlus1 = "google.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(url)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        testee.storeForReauthentication(url, oldPassword)
        testee.storeForReauthentication(url, newPassword)

        val result = testee.retrieveReauthData(url)
        assertEquals(newPassword, result.password)
    }

    @Test
    fun whenClearAllThenAllStoredPasswordsAreRemoved() {
        val googleUrl = "https://accounts.google.com/signin"
        val microsoftUrl = "https://login.microsoftonline.com/signin"
        val googlePassword = "googlePassword123"
        val microsoftPassword = "microsoftPassword456"

        whenever(urlMatcher.extractUrlPartsForAutofill(googleUrl)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = "google.com", userFacingETldPlus1 = "google.com", subdomain = null),
        )
        whenever(urlMatcher.extractUrlPartsForAutofill(microsoftUrl)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = "microsoftonline.com", userFacingETldPlus1 = "microsoftonline.com", subdomain = null),
        )

        testee.storeForReauthentication(googleUrl, googlePassword)
        testee.storeForReauthentication(microsoftUrl, microsoftPassword) // This won't actually store due to eTLD+1 guard

        testee.clearAll()

        val googleResult = testee.retrieveReauthData(googleUrl)
        val microsoftResult = testee.retrieveReauthData(microsoftUrl)

        // Both should return null - Google because it was cleared, Microsoft because it was never stored
        assertNull(googleResult.password)
        assertNull(microsoftResult.password)
    }

    @Test
    fun whenStoreForReauthenticationWithNonGoogleDomainThenPasswordIsNotStored() {
        val exampleUrl = "https://example.com/login"
        val password = "testPassword123"
        val eTldPlus1 = "example.com"

        whenever(urlMatcher.extractUrlPartsForAutofill(exampleUrl)).thenReturn(
            ExtractedUrlParts(eTldPlus1 = eTldPlus1, userFacingETldPlus1 = eTldPlus1, subdomain = null),
        )

        testee.storeForReauthentication(exampleUrl, password)

        val result = testee.retrieveReauthData(exampleUrl)
        assertNull(result.password)
    }
}
