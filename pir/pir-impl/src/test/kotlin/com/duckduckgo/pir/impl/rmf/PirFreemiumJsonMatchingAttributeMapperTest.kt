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

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PirFreemiumJsonMatchingAttributeMapperTest {

    private val testee = PirFreemiumJsonMatchingAttributeMapper()

    @Test
    fun whenEligibleKeyHasBooleanValueThenMapsToEligibleAttribute() {
        val enabled = testee.map("isFreemiumPIREligible", JsonMatchingAttribute(value = true))
        val disabled = testee.map("isFreemiumPIREligible", JsonMatchingAttribute(value = false))

        assertEquals(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = true), enabled)
        assertEquals(PirFreemiumEligibleJsonMatchingAttribute(remoteValue = false), disabled)
    }

    @Test
    fun whenEligibleKeyHasNonBooleanValueThenReturnsNull() {
        assertNull(testee.map("isFreemiumPIREligible", JsonMatchingAttribute(value = "true")))
        assertNull(testee.map("isFreemiumPIREligible", JsonMatchingAttribute(value = null)))
    }

    @Test
    fun whenDidActivateKeyHasBooleanValueThenMapsToDidActivateAttribute() {
        val activated = testee.map("freemiumPIRDidActivate", JsonMatchingAttribute(value = true))
        val notActivated = testee.map("freemiumPIRDidActivate", JsonMatchingAttribute(value = false))

        assertEquals(PirFreemiumDidActivateJsonMatchingAttribute(remoteValue = true), activated)
        assertEquals(PirFreemiumDidActivateJsonMatchingAttribute(remoteValue = false), notActivated)
    }

    @Test
    fun whenDidActivateKeyHasNonBooleanValueThenReturnsNull() {
        assertNull(testee.map("freemiumPIRDidActivate", JsonMatchingAttribute(value = "false")))
        assertNull(testee.map("freemiumPIRDidActivate", JsonMatchingAttribute(value = null)))
    }

    @Test
    fun whenFirstScanResultKeyHasStringValueThenMapsToFirstScanResultAttribute() {
        val result = testee.map("freemiumPIRFirstScanResult", JsonMatchingAttribute(value = "matchesFound"))

        assertEquals(PirFreemiumFirstScanResultJsonMatchingAttribute(remoteValue = "matchesFound"), result)
    }

    @Test
    fun whenFirstScanResultKeyHasNonStringOrBlankValueThenReturnsNull() {
        assertNull(testee.map("freemiumPIRFirstScanResult", JsonMatchingAttribute(value = listOf("matchesFound"))))
        assertNull(testee.map("freemiumPIRFirstScanResult", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("freemiumPIRFirstScanResult", JsonMatchingAttribute(value = "")))
        assertNull(testee.map("freemiumPIRFirstScanResult", JsonMatchingAttribute(value = null)))
    }

    @Test
    fun whenKeyBelongsToAnotherMapperThenReturnsNull() {
        assertNull(testee.map("isCurrentPIRUser", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("isCurrentFreemiumPIRUser", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("isfreemiumpireligible", JsonMatchingAttribute(value = true)))
    }
}
