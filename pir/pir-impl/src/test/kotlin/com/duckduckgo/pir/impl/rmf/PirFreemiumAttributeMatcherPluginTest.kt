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

package com.duckduckgo.pir.impl.rmf

import com.duckduckgo.pir.impl.freemium.PirFreemium
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.NOT_ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.USED
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.MATCHES_FOUND
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.NO_MATCHES
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PirFreemiumAttributeMatcherPluginTest {

    private val pirFreemium: PirFreemium = mock()
    private val dataStore: PirFreemiumDataStore = mock()

    private val testee = PirFreemiumAttributeMatcherPlugin(pirFreemium, dataStore)

    @Test
    fun whenAttributeIsNotAFreemiumAttributeThenReturnsNull() = runTest {
        assertNull(testee.evaluate(PirUserJsonMatchingAttribute(remoteValue = true)))
    }

    @Test
    fun whenUserIsEligibleThenEligibleRuleMatches() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(ELIGIBLE)

        assertEquals(true, testee.evaluate(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = true)))
        assertEquals(false, testee.evaluate(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = false)))
    }

    @Test
    fun whenFreeScanHasCompletedThenUserIsStillEligible() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(USED)

        assertEquals(true, testee.evaluate(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = true)))
    }

    @Test
    fun whenUserSubscribedAfterFreeScanThenEligibleRuleDoesNotMatch() = runTest {
        whenever(pirFreemium.getPirFreemiumState()).thenReturn(NOT_ELIGIBLE)
        whenever(dataStore.didActivate).thenReturn(true)
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(false, testee.evaluate(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = true)))
        assertEquals(true, testee.evaluate(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = false)))
    }

    @Test
    fun whenUserHasNotActivatedThenNotActivatedRuleMatches() = runTest {
        whenever(dataStore.didActivate).thenReturn(false)

        assertEquals(true, testee.evaluate(PirFreemiumDidActivateJsonMatchingAttribute(remoteValue = false)))
        assertEquals(false, testee.evaluate(PirFreemiumDidActivateJsonMatchingAttribute(remoteValue = true)))
    }

    @Test
    fun whenUserActivatedThenNotActivatedRuleStopsMatching() = runTest {
        whenever(dataStore.didActivate).thenReturn(false, true)
        val notActivatedRule = PirFreemiumDidActivateJsonMatchingAttribute(remoteValue = false)

        assertEquals(true, testee.evaluate(notActivatedRule))
        assertEquals(false, testee.evaluate(notActivatedRule))
    }

    @Test
    fun whenMatchesFoundThenOnlyMatchesFoundRuleMatches() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(true, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "matchesFound")))
        assertEquals(false, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "noMatches")))
    }

    @Test
    fun whenNoMatchesThenOnlyNoMatchesRuleMatches() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES)

        assertEquals(true, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "noMatches")))
        assertEquals(false, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "matchesFound")))
    }

    @Test
    fun whenNoScanResultYetThenNeitherResultRuleMatches() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(null)

        assertEquals(false, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "noMatches")))
        assertEquals(false, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "matchesFound")))
    }

    @Test
    fun whenResultMovesToMatchesFoundThenNoMatchesRuleStopsMatching() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES, MATCHES_FOUND)
        val noMatchesRule = PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "noMatches")

        assertEquals(true, testee.evaluate(noMatchesRule))
        assertEquals(false, testee.evaluate(noMatchesRule))
    }

    @Test
    fun whenRemoteValueDiffersOnlyInCaseThenItMatches() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(true, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "MatchesFound")))
    }

    @Test
    fun whenRemoteValueUsesAndroidEnumNameThenItDoesNotMatch() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(false, testee.evaluate(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "MATCHES_FOUND")))
    }
}
