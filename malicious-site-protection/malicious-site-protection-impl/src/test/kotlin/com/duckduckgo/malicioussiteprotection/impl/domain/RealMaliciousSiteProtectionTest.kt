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

package com.duckduckgo.malicioussiteprotection.impl.domain

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import com.duckduckgo.malicioussiteprotection.impl.data.Filter
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.malicioussiteprotection.impl.data.Match
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealMaliciousSiteProtectionTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var realMaliciousSiteProtection: RealMaliciousSiteProtection
    private val maliciousSiteProtectionFeature = FakeFeatureToggleFactory.create(MaliciousSiteProtectionFeature::class.java)
    private val isMainProcess: Boolean = true
    private val maliciousSiteRepository: MaliciousSiteRepository = mock()
    private val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")

    @Before
    fun setup() {
        realMaliciousSiteProtection = RealMaliciousSiteProtection(
            coroutinesTestRule.testDispatcherProvider,
            maliciousSiteProtectionFeature,
            isMainProcess,
            coroutinesTestRule.testScope,
            maliciousSiteRepository,
            messageDigest,
        )
    }

    @Test
    fun isMalicious_returnsFalse_whenUrlIsNotMalicious() = runTest {
        val url = Uri.parse("https://example.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)

        whenever(maliciousSiteRepository.containsHashPrefix(hashPrefix)).thenReturn(false)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertFalse(result)
    }

    @Test
    fun isMalicious_returnsTrue_whenUrlIsMalicious() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*malicious.*")

        whenever(maliciousSiteRepository.containsHashPrefix(hashPrefix)).thenReturn(true)
        whenever(maliciousSiteRepository.getFilter(hash)).thenReturn(filter)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertTrue(result)
    }

    @Test
    fun isMalicious_returnsFalse_whenUrlDoesNotMatchFilter() = runTest {
        val url = Uri.parse("https://safe.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*unsafe.*")

        whenever(maliciousSiteRepository.containsHashPrefix(hashPrefix)).thenReturn(true)
        whenever(maliciousSiteRepository.getFilter(hash)).thenReturn(filter)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertFalse(result)
    }

    @Test
    fun isMalicious_invokesOnSiteBlockedAsync_whenUrlIsMaliciousAndNeedsToGoToNetwork() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*whatever.*")
        var onSiteBlockedAsyncCalled = false

        whenever(maliciousSiteRepository.containsHashPrefix(hashPrefix)).thenReturn(true)
        whenever(maliciousSiteRepository.getFilter(hash)).thenReturn(filter)
        whenever(maliciousSiteRepository.matches(hashPrefix))
            .thenReturn(listOf(Match(hostname, url.toString(), ".*malicious.*", hash)))

        realMaliciousSiteProtection.isMalicious(url) {
            onSiteBlockedAsyncCalled = true
        }

        assertTrue(onSiteBlockedAsyncCalled)
    }
}
