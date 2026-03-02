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

import com.duckduckgo.pir.impl.PirUserUtils
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PirUserAttributeMatcherPluginTest {

    private lateinit var testee: PirUserAttributeMatcherPlugin

    private val mockPirUserUtils: PirUserUtils = mock()

    @Before
    fun setUp() {
        testee = PirUserAttributeMatcherPlugin(mockPirUserUtils)
    }

    @Test
    fun whenEvaluateWithNonPirUserMatchingAttributeThenReturnsNull() = runTest {
        val result = testee.evaluate(FakeMatchingAttribute())

        assertNull(result)
    }

    @Test
    fun whenEvaluateWithPirUserAttributeAndUserIsActiveAndRemoteValueTrueThenReturnsTrue() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(true)
        val attribute = PirUserJsonMatchingAttribute(remoteValue = true)

        val result = testee.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenEvaluateWithPirUserAttributeAndUserIsActiveAndRemoteValueFalseThenReturnsFalse() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(true)
        val attribute = PirUserJsonMatchingAttribute(remoteValue = false)

        val result = testee.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenEvaluateWithPirUserAttributeAndUserIsNotActiveAndRemoteValueTrueThenReturnsFalse() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(false)
        val attribute = PirUserJsonMatchingAttribute(remoteValue = true)

        val result = testee.evaluate(attribute)

        assertFalse(result!!)
    }

    @Test
    fun whenEvaluateWithPirUserAttributeAndUserIsNotActiveAndRemoteValueFalseThenReturnsTrue() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(false)
        val attribute = PirUserJsonMatchingAttribute(remoteValue = false)

        val result = testee.evaluate(attribute)

        assertTrue(result!!)
    }

    @Test
    fun whenRemoteValueMatchesActiveUserStatusThenReturnsTrue() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(true)
        val attributeTrue = PirUserJsonMatchingAttribute(remoteValue = true)
        assertEquals(true, testee.evaluate(attributeTrue))

        whenever(mockPirUserUtils.isActiveUser()).thenReturn(false)
        val attributeFalse = PirUserJsonMatchingAttribute(remoteValue = false)
        assertEquals(true, testee.evaluate(attributeFalse))
    }

    @Test
    fun whenRemoteValueDoesNotMatchActiveUserStatusThenReturnsFalse() = runTest {
        whenever(mockPirUserUtils.isActiveUser()).thenReturn(true)
        val attributeFalse = PirUserJsonMatchingAttribute(remoteValue = false)
        assertEquals(false, testee.evaluate(attributeFalse))

        whenever(mockPirUserUtils.isActiveUser()).thenReturn(false)
        val attributeTrue = PirUserJsonMatchingAttribute(remoteValue = true)
        assertEquals(false, testee.evaluate(attributeTrue))
    }

    private class FakeMatchingAttribute : MatchingAttribute
}
