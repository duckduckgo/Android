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

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuckDuckGoAdClickDataTest {

    private lateinit var testee: DuckDuckGoAdClickData

    @Before
    fun before() {
        testee = DuckDuckGoAdClickData()
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
        testee.addExemption(dummyExpiration("host1"))
        assertTrue(testee.isHostExempted("host1"))

        testee.removeExemption()

        assertFalse(testee.isHostExempted("host1"))
    }

    @Test
    fun whenAddExemptionForActiveTabCalledForADifferentHostThenOnlyThatSecondHostExemptionExists() {
        val host = "host1"
        val otherHost = "host2"
        testee.addExemption(dummyExpiration(host))
        assertEquals(host, testee.getExemption()!!.hostTldPlusOne)

        testee.addExemption(dummyExpiration(otherHost))
        assertEquals(otherHost, testee.getExemption()!!.hostTldPlusOne)
    }

    private fun dummyExpiration(host: String) = Exemption(
        hostTldPlusOne = host,
        navigationExemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10),
        exemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60),
    )
}
