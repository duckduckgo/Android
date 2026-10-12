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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPasswordImportAvailabilityTest {

    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter = mock()
    private val webViewCapabilityChecker: ImportGooglePasswordsCapabilityChecker = mock()

    private val testee = RealPasswordImportAvailability(
        credentialExchangePasswordImporter = credentialExchangePasswordImporter,
        webViewCapabilityChecker = webViewCapabilityChecker,
    )

    @Test
    fun whenBothFlowsAvailableThenCanImport() = runTest {
        configure(credentialExchange = true, webView = true)
        assertTrue(testee.canImport())
    }

    @Test
    fun whenOnlyCredentialExchangeAvailableThenCanImport() = runTest {
        configure(credentialExchange = true, webView = false)
        assertTrue(testee.canImport())
    }

    @Test
    fun whenOnlyWebFlowAvailableThenCanImport() = runTest {
        configure(credentialExchange = false, webView = true)
        assertTrue(testee.canImport())
    }

    @Test
    fun whenNeitherFlowAvailableThenCannotImport() = runTest {
        configure(credentialExchange = false, webView = false)
        assertFalse(testee.canImport())
    }

    private suspend fun configure(credentialExchange: Boolean, webView: Boolean) {
        whenever(credentialExchangePasswordImporter.isSupported()).thenReturn(credentialExchange)
        whenever(webViewCapabilityChecker.webViewCapableOfImporting()).thenReturn(webView)
    }
}
