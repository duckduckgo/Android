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

    @Test
    fun whenMatchesWithAddressesDifferingOnlyByExtrasThenReturnsTrue() {
        val profile1 = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            addresses = listOf(AddressCityState(city = "City", state = "State")),
        )

        val profile2 = ExtractedProfile(
            profileQueryId = 456L,
            brokerName = "test-broker",
            name = "John Doe",
            addresses = listOf(AddressCityState(city = "City", state = "State", extras = mapOf("zip" to "12345"))),
        )

        val result = profile1.matches(profile2)

        assertTrue(result)
    }

    @Test
    fun whenToParamsWithExtrasThenForwardsProfileAndAddressExtras() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            addresses = listOf(
                AddressCityState(
                    city = "Springfield",
                    state = "IL",
                    fullAddress = "100 Sample Dr, Springfield, IL 62701",
                    extras = mapOf("street" to "100 Sample Dr", "zip" to "62701"),
                ),
            ),
            phoneNumbers = listOf("555-1234"),
            relatives = listOf("Jane Doe"),
            identifier = "id123",
            extras = mapOf("county" to "Sangamon"),
        )

        val result = profile.toParams("John Doe")

        assertEquals("35", result.age)
        assertEquals(listOf("555-1234"), result.phoneNumbers)
        assertEquals(listOf("Jane Doe"), result.relatives)
        assertEquals("id123", result.identifier)
        assertEquals(mapOf("county" to "Sangamon"), result.extras)
        assertEquals(1, result.addresses.size)
        assertEquals("Springfield", result.addresses[0].city)
        assertEquals("IL", result.addresses[0].state)
        assertEquals(mapOf("street" to "100 Sample Dr", "zip" to "62701"), result.addresses[0].extras)
    }

    @Test
    fun whenToParamsWithoutExtrasThenSendsEmptyMaps() {
        val profile = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
        )

        val result = profile.toParams("John Doe")

        assertNull(result.age)
        assertNull(result.identifier)
        assertTrue(result.extras.isEmpty())
        assertTrue(result.addresses.isEmpty())
        assertTrue(result.phoneNumbers.isEmpty())
        assertTrue(result.relatives.isEmpty())
    }

    @Test
    fun whenRefreshedWithThenKeepsLocallyOwnedAttributesAndTakesScrapedOnes() {
        val stored = ExtractedProfile(
            dbId = 42L,
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "35",
            relatives = listOf("Jane Doe"),
            dateAddedInMillis = 1000L,
            deprecated = true,
        )

        val scraped = ExtractedProfile(
            dbId = 0L,
            profileQueryId = 123L,
            brokerName = "test-broker",
            name = "John Doe",
            age = "36",
            relatives = listOf("Jane Doe", "Jack Doe"),
            dateAddedInMillis = 0L,
            deprecated = false,
        )

        val result = stored.refreshedWith(scraped)

        assertEquals(42L, result.dbId)
        assertEquals(1000L, result.dateAddedInMillis)
        assertTrue(result.deprecated)
        assertEquals("36", result.age)
        assertEquals(listOf("Jane Doe", "Jack Doe"), result.relatives)
    }

    @Test
    fun whenRefreshedWithThenMergesProfileExtrasKeepingKeysMissingFromTheScrape() {
        val stored = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            extras = mapOf("county" to "Sangamon", "middleName" to "Michael"),
        )

        val scraped = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            extras = mapOf("county" to "Cook"),
        )

        val result = stored.refreshedWith(scraped)

        assertEquals(mapOf("county" to "Cook", "middleName" to "Michael"), result.extras)
    }

    @Test
    fun whenRefreshedWithThenMergesExtrasOfTheAddressWithTheSameCityAndState() {
        val stored = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            addresses = listOf(
                AddressCityState(city = "Springfield", state = "IL", extras = mapOf("street" to "100 Sample Dr", "zip" to "62701")),
                AddressCityState(city = "Boston", state = "MA", extras = mapOf("zip" to "02101")),
            ),
        )

        val scraped = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            addresses = listOf(
                AddressCityState(city = "Springfield", state = "IL", extras = mapOf("zip" to "62702")),
            ),
        )

        val result = stored.refreshedWith(scraped)

        assertEquals(1, result.addresses.size)
        assertEquals(mapOf("street" to "100 Sample Dr", "zip" to "62702"), result.addresses[0].extras)
    }

    @Test
    fun whenRefreshedWithNewAddressThenKeepsOnlyItsOwnExtras() {
        val stored = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            addresses = listOf(AddressCityState(city = "Springfield", state = "IL", extras = mapOf("street" to "100 Sample Dr"))),
        )

        val scraped = ExtractedProfile(
            profileQueryId = 123L,
            brokerName = "test-broker",
            addresses = listOf(AddressCityState(city = "Boston", state = "MA", extras = mapOf("zip" to "02101"))),
        )

        val result = stored.refreshedWith(scraped)

        assertEquals(listOf(AddressCityState(city = "Boston", state = "MA", extras = mapOf("zip" to "02101"))), result.addresses)
    }
}
