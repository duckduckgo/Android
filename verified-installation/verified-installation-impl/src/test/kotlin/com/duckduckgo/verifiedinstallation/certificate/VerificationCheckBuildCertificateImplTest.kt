/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.verifiedinstallation.certificate

import com.duckduckgo.verifiedinstallation.certificate.VerificationCheckBuildCertificateImpl.Companion.PRODUCTION_SHA_256_HASH
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VerificationCheckBuildCertificateImplTest {

    private val certHashExtractor: SigningCertificateHashExtractor = mock()
    private val testee = VerificationCheckBuildCertificateImpl(certHashExtractor)

    @Test
    fun whenExtractedHashIsNullThenNotAMatch() {
        whenever(certHashExtractor.sha256Hash()).thenReturn(null)
        assertFalse(testee.builtWithVerifiedCertificate())
    }

    @Test
    fun whenExtractedHashIsNotProductionHashThenNotAMatch() {
        whenever(certHashExtractor.sha256Hash()).thenReturn("ABC-123")
        assertFalse(testee.builtWithVerifiedCertificate())
    }

    @Test
    fun whenExtractedHashIsProductionHashThenIsAMatch() {
        whenever(certHashExtractor.sha256Hash()).thenReturn(PRODUCTION_SHA_256_HASH)
        assertTrue(testee.builtWithVerifiedCertificate())
    }
}
