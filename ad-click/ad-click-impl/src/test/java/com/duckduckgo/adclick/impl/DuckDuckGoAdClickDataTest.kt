/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl

import com.duckduckgo.adclick.impl.store.exemptions.AdClickExemptionsDao
import com.duckduckgo.adclick.impl.store.exemptions.AdClickExemptionsDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckDuckGoAdClickDataTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckDuckGoAdClickData

    private val mockDatabase: AdClickExemptionsDatabase = mock()
    private val mockAdClickExemptionsDao: AdClickExemptionsDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.adClickExemptionsDao()).thenReturn(mockAdClickExemptionsDao)
        testee = DuckDuckGoAdClickData(
            database = mockDatabase,
            coroutineScope = TestScope(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )
    }

    @Test
    fun whenSetAdDomainForActiveTabCalledThenTheSameDomainIsRetrieved() {
        testee.setAdDomainTldPlusOne("host")

        assertEquals("host", testee.getAdDomainTldPlusOne())
    }

    @Test
    fun whenRemoveAdDomainForActiveTabCalledThenAdDomainForTabIsRemoved() {
        testee.setAdDomainTldPlusOne("host")
        assertEquals("host", testee.getAdDomainTldPlusOne())

        testee.removeAdDomain()

        assertEquals(null, testee.getAdDomainTldPlusOne())
    }

    @Test
    fun whenRemoveExemptionForActiveTabCalledThenAllExemptionForTabAreRemoved() {
        testee.addExemption(exemptionWithExpiration("host1"))
        assertTrue(testee.isHostExempted("host1"))

        testee.removeExemption()

        assertFalse(testee.isHostExempted("host1"))
    }

    @Test
    fun whenAddExemptionForActiveTabCalledForADifferentHostThenOnlyThatSecondHostExemptionExists() {
        val host = "host1"
        val otherHost = "host2"

        testee.addExemption(exemptionWithExpiration(host))
        assertEquals(host, testee.getExemption()!!.hostTldPlusOne)
        verify(mockAdClickExemptionsDao).insertTabExemption(any())
        reset(mockAdClickExemptionsDao)

        testee.addExemption(exemptionWithExpiration(otherHost))
        assertEquals(otherHost, testee.getExemption()!!.hostTldPlusOne)
        verify(mockAdClickExemptionsDao).insertTabExemption(any())
    }

    @Test
    fun whenAddExemptionForTabCalledForADifferentHostThenOnlyThatSecondHostExemptionExists() {
        val host = "host1"
        val otherHost = "host2"
        val tabId = "tabId"

        testee.addExemption(tabId, exemptionWithExpiration(host))
        assertEquals(host, testee.getExemption(tabId)!!.hostTldPlusOne)
        verify(mockAdClickExemptionsDao).insertTabExemption(any())
        reset(mockAdClickExemptionsDao)

        testee.addExemption(tabId, exemptionWithExpiration(otherHost))
        assertEquals(otherHost, testee.getExemption(tabId)!!.hostTldPlusOne)
        verify(mockAdClickExemptionsDao).insertTabExemption(any())
    }

    private fun exemptionWithExpiration(host: String) = Exemption(
        hostTldPlusOne = host,
        navigationExemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10),
        exemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60),
    )
}
