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
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Ignored
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Safe
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionRCFeature
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSet
import com.duckduckgo.malicioussiteprotection.impl.models.Match
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult.Result
import com.duckduckgo.malicioussiteprotection.impl.remoteconfig.MaliciousSiteProtectionRCRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class RealMaliciousSiteProtectionTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var realMaliciousSiteProtection: RealMaliciousSiteProtection
    private val mockMaliciousSiteRepository: MaliciousSiteRepository = mock()
    private val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
    private val mockMaliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature = mock()
    private val mockMaliciousSiteProtectionRCRepository: MaliciousSiteProtectionRCRepository = mock()
    private val urlCanonicalization: UrlCanonicalization = mock()

    @Before
    fun setup() {
        realMaliciousSiteProtection = RealMaliciousSiteProtection(
            coroutinesTestRule.testDispatcherProvider,
            coroutinesTestRule.testScope,
            mockMaliciousSiteRepository,
            mockMaliciousSiteProtectionRCRepository,
            messageDigest,
            mockMaliciousSiteProtectionRCFeature,
            urlCanonicalization,
        )
        whenever(mockMaliciousSiteProtectionRCFeature.isFeatureEnabled()).thenReturn(true)
        whenever(urlCanonicalization.canonicalizeUrl(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun isMalicious_returnsSafe_whenUrlIsNotMalicious() = runTest {
        val url = Uri.parse("https://example.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(null)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(ConfirmedResult(Safe), result)
    }

    @Test
    fun isMalicious_returnsMalicious_whenUrlIsMalicious() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*malicious.*")

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(ConfirmedResult(Malicious(PHISHING)), result)
    }

    @Test
    fun isMalicious_returnsIgnored_whenUrlIsScamButScamProtectionDisabled() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*malicious.*")

        whenever(mockMaliciousSiteProtectionRCFeature.scamProtectionEnabled()).thenReturn(false)
        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(SCAM)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(ConfirmedResult(Ignored), result)
    }

    @Test
    fun isMalicious_returnsIgnored_whenUrlIsMaliciousButRCFeatureDisabled() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*malicious.*")

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))
        whenever(mockMaliciousSiteProtectionRCFeature.isFeatureEnabled()).thenReturn(false)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(ConfirmedResult(Ignored), result)
    }

    @Test
    fun isMalicious_returnsSafe_whenUrlIsMaliciousButDomainMatchesException() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*malicious.*")

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))
        whenever(mockMaliciousSiteProtectionRCRepository.isExempted(hostname)).thenReturn(true)

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(ConfirmedResult(Safe), result)
    }

    @Test
    fun isMalicious_returnsWaitForConfirmation_whenUrlDoesNotMatchFilter() = runTest {
        val url = Uri.parse("https://safe.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*unsafe.*")

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))

        val result = realMaliciousSiteProtection.isMalicious(url) {}

        assertEquals(MaliciousSiteProtection.IsMaliciousResult.WaitForConfirmation, result)
    }

    @Test
    fun isMalicious_invokesOnSiteBlockedAsync_whenUrlIsMaliciousAndNeedsToGoToNetwork() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*whatever.*")
        var onSiteBlockedAsyncCalled = false

        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))
        whenever(mockMaliciousSiteRepository.matches(hashPrefix.substring(0, 4)))
            .thenReturn(Result(listOf(Match(hostname, url.toString(), ".*malicious.*", hash, PHISHING))))

        realMaliciousSiteProtection.isMalicious(url) {
            onSiteBlockedAsyncCalled = true
        }

        assertTrue(onSiteBlockedAsyncCalled)
    }

    @Test
    fun isMalicious_InvokesOnSiteBlockedAsyncWithIgnored_whenUrlIsScamAndNeedsToGoToNetworkButScamProtectionIsDisabled() = runTest {
        val url = Uri.parse("https://malicious.com")
        val hostname = url.host!!
        val hash = messageDigest.digest(hostname.toByteArray()).joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)
        val filter = Filter(hash, ".*whatever.*")
        var onSiteBlockedAsyncCalled = false
        var maliciousStatus: MaliciousStatus? = null

        whenever(mockMaliciousSiteProtectionRCFeature.scamProtectionEnabled()).thenReturn(false)
        whenever(mockMaliciousSiteRepository.getFeedForHashPrefix(hashPrefix)).thenReturn(PHISHING)
        whenever(mockMaliciousSiteRepository.getFilters(hash)).thenReturn(FilterSet(filter, PHISHING))
        whenever(mockMaliciousSiteRepository.matches(hashPrefix.substring(0, 4)))
            .thenReturn(Result(listOf(Match(hostname, url.toString(), ".*malicious.*", hash, SCAM))))

        realMaliciousSiteProtection.isMalicious(url) { status: MaliciousStatus ->
            onSiteBlockedAsyncCalled = true
            maliciousStatus = status
        }

        assertTrue(onSiteBlockedAsyncCalled)
        assertEquals(Ignored, maliciousStatus)
    }
}
