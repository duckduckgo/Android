/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PirUtilsTest {

    @Test
    fun whenSplitIntoPartsWithEmptyListThenReturnsEmptyList() {
        val emptyList = emptyList<Int>()

        val result = emptyList.splitIntoParts(3)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenSplitIntoPartsWithEvenDivisionThenSplitsEvenly() {
        val list = listOf(1, 2, 3, 4, 5, 6)

        val result = list.splitIntoParts(3)

        assertEquals(3, result.size)
        assertEquals(listOf(1, 2), result[0])
        assertEquals(listOf(3, 4), result[1])
        assertEquals(listOf(5, 6), result[2])
    }

    @Test
    fun whenSplitIntoPartsWithUnevenDivisionThenDistributesRemainder() {
        val list = listOf(1, 2, 3, 4, 5, 6, 7)

        val result = list.splitIntoParts(3)

        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result[0])
        assertEquals(listOf(4, 5), result[1])
        assertEquals(listOf(6, 7), result[2])
    }

    @Test
    fun whenSplitIntoPartsWithSinglePartThenReturnsOriginalList() {
        val list = listOf(1, 2, 3, 4, 5)

        val result = list.splitIntoParts(1)

        assertEquals(1, result.size)
        assertEquals(listOf(1, 2, 3, 4, 5), result[0])
    }

    @Test
    fun whenSplitIntoPartsWithMorePartsThanElementsThenSomePartsAreEmpty() {
        val list = listOf(1, 2, 3)

        val result = list.splitIntoParts(5)

        assertEquals(5, result.size)
        assertEquals(listOf(1), result[0])
        assertEquals(listOf(2), result[1])
        assertEquals(listOf(3), result[2])
        assertEquals(emptyList<Int>(), result[3])
        assertEquals(emptyList<Int>(), result[4])
    }

    @Test
    fun whenSplitIntoPartsWithLargeListThenDistributesCorrectly() {
        val list = (1..10).toList()

        val result = list.splitIntoParts(3)

        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3, 4), result[0])
        assertEquals(listOf(5, 6, 7), result[1])
        assertEquals(listOf(8, 9, 10), result[2])
    }

    @Test
    fun whenToParamsWithFilledProfileThenReturnsCorrectParams() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            profileUrl = "https://example.com/profile",
            email = "john@example.com",
        )

        val result = profile.toParams("John Doe")

        assertEquals("John Doe", result.name)
        assertEquals("https://example.com/profile", result.profileUrl)
        assertEquals("John Doe", result.fullName)
        assertEquals("john@example.com", result.email)
    }

    @Test
    fun whenToParamsWithEmptyFieldsThenReturnsNullForEmptyFields() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "",
            profileUrl = "",
            email = "",
        )

        val result = profile.toParams("")

        assertNull(result.name)
        assertNull(result.profileUrl)
        assertNull(result.fullName)
        assertNull(result.email)
    }

    @Test
    fun whenToParamsWithMixedFieldsThenConvertsCorrectly() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "Jane Smith",
            profileUrl = "",
            email = "jane@example.com",
        )

        val result = profile.toParams("Jane Smith")

        assertEquals("Jane Smith", result.name)
        assertNull(result.profileUrl)
        assertEquals("Jane Smith", result.fullName)
        assertEquals("jane@example.com", result.email)
    }

    @Test
    fun whenHasMatchingProfileOnParentWithMatchThenReturnsTrue() {
        val address1 = AddressCityState(city = "City", state = "State", fullAddress = "123 Main St")
        val address2 = AddressCityState(city = "City", state = "State", fullAddress = "456 Oak Ave")

        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(address1),
        )

        val parentProfile = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(address1, address2),
        )

        val result = profile.hasMatchingProfileOnParent(listOf(parentProfile))

        assertTrue(result)
    }

    @Test
    fun whenHasMatchingProfileOnParentWithNoMatchThenReturnsFalse() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
        )

        val parentProfile = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "Jane Smith",
            age = "30",
        )

        val result = profile.hasMatchingProfileOnParent(listOf(parentProfile))

        assertFalse(result)
    }

    @Test
    fun whenHasMatchingProfileOnParentWithDifferentBrokerNameThenReturnsFalse() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
        )

        val parentProfile = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "different-broker",
            name = "John Doe",
            age = "35",
        )

        val result = profile.hasMatchingProfileOnParent(listOf(parentProfile))

        assertFalse(result)
    }

    @Test
    fun whenHasMatchingProfileOnParentWithEmptyListThenReturnsFalse() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
        )

        val result = profile.hasMatchingProfileOnParent(emptyList())

        assertFalse(result)
    }

    @Test
    fun whenMatchesWithIdenticalProfilesThenReturnsTrue() {
        val address1 = AddressCityState(city = "City", state = "State", fullAddress = "123 Main St")

        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(address1),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(address1),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithDifferentNamesThenReturnsFalse() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "Jane Smith",
            age = "35",
        )

        val result = profile1.matches(profile2)

        assertFalse(result)
    }

    @Test
    fun whenMatchesWithDifferentAgesThenReturnsFalse() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "40",
        )

        val result = profile1.matches(profile2)

        assertFalse(result)
    }

    @Test
    fun whenMatchesWithAlternativeNamesSubsetThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John", "JD"),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithAlternativeNamesSupersetThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John", "JD"),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithRelativesSubsetThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            relatives = listOf("Jane Doe"),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            relatives = listOf("Jane Doe", "Jack Doe"),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithAddressesSubsetThenReturnsTrue() {
        val address1 = AddressCityState(city = "City", state = "State", fullAddress = "123 Main St")
        val address2 = AddressCityState(city = "City", state = "State", fullAddress = "456 Oak Ave")

        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            addresses = listOf(address1),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            addresses = listOf(address1, address2),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithDisjointAlternativeNamesThenReturnsFalse() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John"),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("JD", "Jack"),
        )

        val result = profile1.matches(profile2)

        assertFalse(result)
    }

    @Test
    fun whenMatchesWithEmptyListsThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = emptyList(),
            relatives = emptyList(),
            addresses = emptyList(),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = emptyList(),
            relatives = emptyList(),
            addresses = emptyList(),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithOneEmptyListAndOneNonEmptyThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = emptyList(),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny"),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenMatchesWithComplexProfilesThenEvaluatesCorrectly() {
        val address1 = AddressCityState(city = "City", state = "State", fullAddress = "123 Main St")
        val address2 = AddressCityState(city = "City", state = "State", fullAddress = "456 Oak Ave")

        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John"),
            relatives = listOf("Jane Doe"),
            addresses = listOf(address1),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            alternativeNames = listOf("Johnny", "John", "JD"),
            relatives = listOf("Jane Doe", "Jack Doe"),
            addresses = listOf(address1, address2),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }
}
