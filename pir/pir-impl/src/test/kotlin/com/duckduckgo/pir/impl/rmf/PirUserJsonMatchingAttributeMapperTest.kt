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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PirUserJsonMatchingAttributeMapperTest {

    private lateinit var testee: PirUserJsonMatchingAttributeMapper

    @Before
    fun setUp() {
        testee = PirUserJsonMatchingAttributeMapper()
    }

    @Test
    fun whenMapWithCorrectKeyAndValueTrueThenReturnsPirUserJsonMatchingAttribute() {
        val result = testee.map("isCurrentPIRUser", JsonMatchingAttribute(value = true))

        assertTrue(result is PirUserJsonMatchingAttribute)
        assertEquals(true, (result as PirUserJsonMatchingAttribute).remoteValue)
    }

    @Test
    fun whenMapWithCorrectKeyAndValueFalseThenReturnsPirUserJsonMatchingAttribute() {
        val result = testee.map("isCurrentPIRUser", JsonMatchingAttribute(value = false))

        assertTrue(result is PirUserJsonMatchingAttribute)
        assertEquals(false, (result as PirUserJsonMatchingAttribute).remoteValue)
    }

    @Test
    fun whenMapWithCorrectKeyAndValueNullThenReturnsNull() {
        val result = testee.map("isCurrentPIRUser", JsonMatchingAttribute(value = null))

        assertNull(result)
    }

    @Test
    fun whenMapWithWrongKeyThenReturnsNull() {
        assertNull(testee.map("wrongKey", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("wrongKey", JsonMatchingAttribute(value = false)))
        assertNull(testee.map("wrongKey", JsonMatchingAttribute(value = null)))
    }

    @Test
    fun whenMapWithEmptyKeyThenReturnsNull() {
        val result = testee.map("", JsonMatchingAttribute(value = true))

        assertNull(result)
    }

    @Test
    fun whenMapWithSimilarKeyThenReturnsNull() {
        assertNull(testee.map("isCurrentPIRuser", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("IsCurrentPIRUser", JsonMatchingAttribute(value = true)))
        assertNull(testee.map("iscurrentpiruser", JsonMatchingAttribute(value = true)))
    }

    @Test
    fun whenMapWithKeyConstantThenReturnsPirUserJsonMatchingAttribute() {
        val result = testee.map(PirUserJsonMatchingAttribute.KEY, JsonMatchingAttribute(value = true))

        assertTrue(result is PirUserJsonMatchingAttribute)
        assertEquals(true, (result as PirUserJsonMatchingAttribute).remoteValue)
    }
}
