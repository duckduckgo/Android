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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.DefaultBrowser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OneUiVersionMatchingAttributeMapperTest {

    private val testee = OneUiVersionMatchingAttributeMapper()

    @Test
    fun whenKeyIsOneUiVersionThenMapsMinMaxAndValue() {
        val result = testee.map("oneUiVersion", JsonMatchingAttribute(min = "8", max = "9.1", value = ""))

        assertEquals(OneUiVersionMatchingAttribute(min = "8", max = "9.1", value = ""), result)
    }

    @Test
    fun whenKeyIsOneUiVersionAndFieldsAreMissingThenDefaultsToEmptyStrings() {
        val result = testee.map("oneUiVersion", JsonMatchingAttribute())

        assertEquals(OneUiVersionMatchingAttribute(min = "", max = "", value = ""), result)
    }

    @Test
    fun whenKeyIsOneUiVersionAndAFieldIsAnIntegralNumberThenCoercesToIntegerString() {
        val result = testee.map("oneUiVersion", JsonMatchingAttribute(min = 8.0))

        assertEquals(OneUiVersionMatchingAttribute(min = "8"), result)
    }

    @Test
    fun whenKeyIsOneUiVersionAndAFieldIsANonIntegralNumberThenMarksAttributeMalformed() {
        val result = testee.map("oneUiVersion", JsonMatchingAttribute(min = 8.1, max = "9"))

        assertEquals(OneUiVersionMatchingAttribute(min = "", max = "9", isMalformed = true), result)
    }

    @Test
    fun whenKeyIsOneUiVersionAndAFieldIsAnotherTypeThenMarksAttributeMalformed() {
        val result = testee.map("oneUiVersion", JsonMatchingAttribute(min = true))

        assertEquals(OneUiVersionMatchingAttribute(min = "", isMalformed = true), result)
    }

    @Test
    fun whenKeyIsNotOneUiVersionThenReturnsNull() {
        assertNull(testee.map("appVersion", JsonMatchingAttribute(min = "8")))
    }
}

class OneUiVersionAttributeMatcherPluginTest {

    private val provider: OneUiVersionProvider = mock()

    private val testee = OneUiVersionAttributeMatcherPlugin(provider)

    @Test
    fun whenProviderReturnsNullThenReturnsFalse() = runTest {
        whenever(provider.get()).thenReturn(null)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(min = "1")))
    }

    @Test
    fun whenMinOnlyAndDeviceAboveThenTrue() = runTest {
        givenDevice(8, 0)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(min = "7")))
    }

    @Test
    fun whenMinOnlyAndDeviceEqualThenTrue() = runTest {
        givenDevice(8, 0)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(min = "8.0")))
    }

    @Test
    fun whenMinOnlyAndDeviceBelowThenFalse() = runTest {
        givenDevice(7, 1)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(min = "8")))
    }

    @Test
    fun whenMaxOnlyAndDeviceAboveThenFalse() = runTest {
        givenDevice(9, 0)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(max = "8")))
    }

    @Test
    fun whenMaxIsMajorOnlyAndDeviceHasSameMajorThenTrue() = runTest {
        givenDevice(8, 5)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(max = "8")))
    }

    @Test
    fun whenMinAndMaxAndDeviceInsideThenTrue() = runTest {
        givenDevice(8, 5)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(min = "8", max = "9")))
    }

    @Test
    fun whenMinHasMoreComponentsThanDeviceAndDeviceIsBelowThenFalse() = runTest {
        givenDevice(8, 0)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(min = "8.0.1")))
    }

    @Test
    fun whenMinHasMoreComponentsThanDeviceAndDeviceIsAboveThenTrue() = runTest {
        givenDevice(8, 1)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(min = "8.0.1")))
    }

    @Test
    fun whenValueIsMajorOnlyThenMatchesAnyMinor() = runTest {
        givenDevice(8, 3)
        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(value = "8")))

        givenDevice(9, 0)
        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(value = "8")))
    }

    @Test
    fun whenValueIsMajorAndMinorThenMatchesExactly() = runTest {
        givenDevice(8, 0)
        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(value = "8.0")))

        givenDevice(8, 1)
        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(value = "8.0")))
    }

    @Test
    fun whenValueHasMoreComponentsThanDeviceThenDeviceIsZeroPadded() = runTest {
        givenDevice(8, 0)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(value = "8.0.0")))
        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(value = "8.0.1")))
    }

    @Test
    fun whenValueAndRangeAreBothSetThenValueWins() = runTest {
        givenDevice(8, 0)

        assertEquals(true, testee.evaluate(OneUiVersionMatchingAttribute(value = "8", min = "9")))
    }

    @Test
    fun whenMinIsNotNumericThenReturnsFalse() = runTest {
        givenDevice(8, 0)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(min = "abc")))
    }

    @Test
    fun whenValueIsNotNumericThenReturnsFalse() = runTest {
        givenDevice(8, 0)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute(value = "8.x")))
    }

    @Test
    fun whenMinIsNonIntegralNumberAndMaxIsValidThenReturnsFalse() = runTest {
        givenDevice(8, 0)
        val attribute = OneUiVersionMatchingAttributeMapper().map("oneUiVersion", JsonMatchingAttribute(min = 8.1, max = "9"))!!

        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun whenValueIsAnotherTypeAndMinIsValidThenReturnsFalse() = runTest {
        givenDevice(8, 0)
        val attribute = OneUiVersionMatchingAttributeMapper().map("oneUiVersion", JsonMatchingAttribute(value = true, min = "7"))!!

        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun whenAttributeIsEmptyThenReturnsFalse() = runTest {
        givenDevice(8, 0)

        assertEquals(false, testee.evaluate(OneUiVersionMatchingAttribute()))
    }

    @Test
    fun whenAttributeIsDifferentThenReturnsNullWithoutQueryingProvider() = runTest {
        assertNull(testee.evaluate(DefaultBrowser(value = true)))

        verify(provider, never()).get()
    }

    private fun givenDevice(major: Int, minor: Int) {
        whenever(provider.get()).thenReturn(OneUiVersion(major, minor))
    }
}
