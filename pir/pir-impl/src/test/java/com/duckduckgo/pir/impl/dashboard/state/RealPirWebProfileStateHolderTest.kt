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

package com.duckduckgo.pir.impl.dashboard.state

import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ProfileQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealPirWebProfileStateHolderTest {

    private lateinit var testee: RealPirWebProfileStateHolder

    @Before
    fun setUp() {
        testee = RealPirWebProfileStateHolder()
    }

    // Tests for setLoadedProfileQueries method
    @Test
    fun whenSetLoadedProfileQueriesWithSingleProfileThenLoadsCorrectly() {
        // Given
        val profileQuery = createProfileQuery(
            firstName = "John",
            middleName = "Michael",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            birthYear = 1990,
        )

        // When
        testee.setLoadedProfileQueries(listOf(profileQuery))

        // Then
        assertTrue(testee.isProfileComplete)
        val resultProfiles = testee.toProfileQueries(2025)
        assertEquals(1, resultProfiles.size)
        with(resultProfiles[0]) {
            assertEquals("John", firstName)
            assertEquals("Michael", middleName)
            assertEquals("Doe", lastName)
            assertEquals("New York", city)
            assertEquals("NY", state)
            assertEquals(1990, birthYear)
        }
    }

    @Test
    fun whenSetLoadedProfileQueriesWithMultipleProfilesThenDeduplicatesNamesAndAddresses() {
        // Given
        val profile1 = createProfileQuery(
            firstName = "John",
            middleName = "Michael",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            birthYear = 1990,
        )
        val profile2 = createProfileQuery(
            firstName = "John", // Same name
            middleName = "Michael",
            lastName = "Doe",
            city = "Los Angeles", // Different address
            state = "CA",
            birthYear = 1990,
        )
        val profile3 = createProfileQuery(
            firstName = "Jane", // Different name
            middleName = null,
            lastName = "Smith",
            city = "New York", // Same address as profile1
            state = "NY",
            birthYear = 1985,
        )

        // When
        testee.setLoadedProfileQueries(listOf(profile1, profile2, profile3))

        // Then
        assertTrue(testee.isProfileComplete)
        val resultProfiles = testee.toProfileQueries(2025)
        assertEquals(4, resultProfiles.size) // 2 names × 2 addresses

        // Verify combinations
        val combinations = resultProfiles.map { "${it.firstName} ${it.lastName} - ${it.city}, ${it.state}" }
        assertTrue(combinations.contains("John Doe - New York, NY"))
        assertTrue(combinations.contains("John Doe - Los Angeles, CA"))
        assertTrue(combinations.contains("Jane Smith - New York, NY"))
        assertTrue(combinations.contains("Jane Smith - Los Angeles, CA"))
    }

    @Test
    fun whenSetLoadedProfileQueriesWithBlankMiddleNameThenIgnoresMiddleName() {
        // Given
        val profileWithBlankMiddle = createProfileQuery(
            firstName = "John",
            middleName = "   ", // Blank middle name
            lastName = "Doe",
            city = "Boston",
            state = "MA",
            birthYear = 1990,
        )

        // When
        testee.setLoadedProfileQueries(listOf(profileWithBlankMiddle))

        // Then
        val resultProfiles = testee.toProfileQueries(2025)
        assertEquals(1, resultProfiles.size)
        assertEquals(null, resultProfiles[0].middleName) // Should be null, not blank
        assertEquals("John Doe", resultProfiles[0].fullName) // Should not include middle name
    }

    @Test
    fun whenSetLoadedProfileQueriesWithMultipleBirthYearsThenUsesFirstNonZero() {
        // Given
        val profile1 = createProfileQuery(
            firstName = "John",
            lastName = "Doe",
            city = "City1",
            state = "ST",
            birthYear = 0, // Zero birth year
        )
        val profile2 = createProfileQuery(
            firstName = "Jane",
            lastName = "Smith",
            city = "City2",
            state = "ST",
            birthYear = 1990, // First non-zero
        )
        val profile3 = createProfileQuery(
            firstName = "Bob",
            lastName = "Johnson",
            city = "City3",
            state = "ST",
            birthYear = 1985, // Second non-zero, should be ignored
        )

        // When
        testee.setLoadedProfileQueries(listOf(profile1, profile2, profile3))

        // Then
        val resultProfiles = testee.toProfileQueries(2025)
        resultProfiles.forEach { profile ->
            assertEquals(1990, profile.birthYear) // All should have the first non-zero birth year
            assertEquals(35, profile.age) // Age calculated from 1990
        }
    }

    @Test
    fun whenSetLoadedProfileQueriesCalledThenClearsExistingData() {
        // Given - Add some initial data
        testee.addName("Initial", null, "Name")
        testee.addAddress("Initial City", "IC")
        testee.setBirthYear(2000)
        assertTrue(testee.isProfileComplete)

        val newProfile = createProfileQuery(
            firstName = "New",
            lastName = "Profile",
            city = "New City",
            state = "NC",
            birthYear = 1995,
        )

        // When
        testee.setLoadedProfileQueries(listOf(newProfile))

        // Then - Only new data should exist
        val resultProfiles = testee.toProfileQueries(2025)
        assertEquals(1, resultProfiles.size)
        with(resultProfiles[0]) {
            assertEquals("New", firstName)
            assertEquals("Profile", lastName)
            assertEquals("New City", city)
            assertEquals("NC", state)
            assertEquals(1995, birthYear)
        }
    }

    @Test
    fun whenSetLoadedProfileQueriesWithEmptyListThenClearsAll() {
        // Given - Add some initial data
        testee.addName("Test", null, "User")
        testee.addAddress("Test City", "TC")
        testee.setBirthYear(1990)
        assertTrue(testee.isProfileComplete)

        // When
        testee.setLoadedProfileQueries(emptyList())

        // Then
        assertFalse(testee.isProfileComplete)
        assertEquals(0, testee.toProfileQueries(2025).size)
    }

    @Test
    fun whenSetLoadedProfileQueriesWithDuplicateProfilesThenDeduplicates() {
        // Given - Identical profiles
        val profile1 = createProfileQuery(
            firstName = "John",
            middleName = "Michael",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            birthYear = 1990,
        )
        val profile2 = createProfileQuery(
            firstName = "John",
            middleName = "Michael",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            birthYear = 1990,
        )

        // When
        testee.setLoadedProfileQueries(listOf(profile1, profile2))

        // Then - Should only have one combination since name and address are identical
        val resultProfiles = testee.toProfileQueries(2025)
        assertEquals(1, resultProfiles.size)
        with(resultProfiles[0]) {
            assertEquals("John", firstName)
            assertEquals("Michael", middleName)
            assertEquals("Doe", lastName)
            assertEquals("New York", city)
            assertEquals("NY", state)
        }
    }

    @Test
    fun whenStateHolderIsInitializedThenIsProfileCompleteReturnsFalse() {
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenAllRequiredDataIsAddedThenIsProfileCompleteReturnsTrue() {
        // Given
        testee.addName("John", null, "Doe")
        testee.addAddress("New York", "NY")
        testee.setBirthYear(1990)

        // Then
        assertTrue(testee.isProfileComplete)
    }

    @Test
    fun whenOnlyNameIsAddedThenIsProfileCompleteReturnsFalse() {
        // Given
        testee.addName("John", null, "Doe")

        // Then
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenOnlyAddressIsAddedThenIsProfileCompleteReturnsFalse() {
        // Given
        testee.addAddress("New York", "NY")

        // Then
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenOnlyBirthYearIsSetThenIsProfileCompleteReturnsFalse() {
        // Given
        testee.setBirthYear(1990)

        // Then
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenValidAddressIsAddedThenReturnsTrue() {
        // When
        val result = testee.addAddress("New York", "NY")

        // Then
        assertTrue(result)
    }

    @Test
    fun whenDuplicateAddressIsAddedThenReturnsFalse() {
        // Given
        testee.addAddress("New York", "NY")

        // When
        val result = testee.addAddress("New York", "NY")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenDifferentAddressesAreAddedThenBothReturnTrue() {
        // When
        val result1 = testee.addAddress("New York", "NY")
        val result2 = testee.addAddress("Los Angeles", "CA")

        // Then
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun whenValidNameIsAddedThenReturnsTrue() {
        // When
        val result = testee.addName("John", null, "Doe")

        // Then
        assertTrue(result)
    }

    @Test
    fun whenValidNameWithMiddleNameIsAddedThenReturnsTrue() {
        // When
        val result = testee.addName("John", "Michael", "Doe")

        // Then
        assertTrue(result)
    }

    @Test
    fun whenDuplicateNameIsAddedThenReturnsFalse() {
        // Given
        testee.addName("John", "Michael", "Doe")

        // When
        val result = testee.addName("John", "Michael", "Doe")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenDifferentNamesAreAddedThenBothReturnTrue() {
        // When
        val result1 = testee.addName("John", null, "Doe")
        val result2 = testee.addName("Jane", null, "Smith")

        // Then
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun whenBirthYearIsSetThenReturnsTrue() {
        // When
        val result = testee.setBirthYear(1990)

        // Then
        assertTrue(result)
    }

    @Test
    fun whenBirthYearIsUpdatedThenReturnsTrue() {
        // Given
        testee.setBirthYear(1990)

        // When
        val result = testee.setBirthYear(1985)

        // Then
        assertTrue(result)
    }

    @Test
    fun whenValidIndexIsUsedToSetNameThenReturnsTrue() {
        // Given
        testee.addName("John", null, "Doe")

        // When
        val result = testee.setNameAtIndex(0, "Jane", null, "Smith")

        // Then
        assertTrue(result)
    }

    @Test
    fun whenInvalidIndexIsUsedToSetNameThenReturnsFalse() {
        // When
        val result = testee.setNameAtIndex(0, "Jane", null, "Smith")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenDuplicateNameIsSetAtIndexThenReturnsFalse() {
        // Given
        testee.addName("John", null, "Doe")
        testee.addName("Jane", null, "Smith")

        // When
        val result = testee.setNameAtIndex(0, "Jane", null, "Smith")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenValidIndexIsUsedToSetAddressThenReturnsTrue() {
        // Given
        testee.addAddress("New York", "NY")

        // When
        val result = testee.setAddressAtIndex(0, "Los Angeles", "CA")

        // Then
        assertTrue(result)
    }

    @Test
    fun whenInvalidIndexIsUsedToSetAddressThenReturnsFalse() {
        // When
        val result = testee.setAddressAtIndex(0, "Los Angeles", "CA")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenDuplicateAddressIsSetAtIndexThenReturnsFalse() {
        // Given
        testee.addAddress("New York", "NY")
        testee.addAddress("Los Angeles", "CA")

        // When
        val result = testee.setAddressAtIndex(0, "Los Angeles", "CA")

        // Then
        assertFalse(result)
    }

    @Test
    fun whenValidIndexIsUsedToRemoveAddressThenReturnsTrue() {
        // Given
        testee.addAddress("New York", "NY")

        // When
        val result = testee.removeAddressAtIndex(0)

        // Then
        assertTrue(result)
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenInvalidIndexIsUsedToRemoveAddressThenReturnsFalse() {
        // When
        val result = testee.removeAddressAtIndex(0)

        // Then
        assertFalse(result)
    }

    @Test
    fun whenValidIndexIsUsedToRemoveNameThenReturnsTrue() {
        // Given
        testee.addName("John", null, "Doe")

        // When
        val result = testee.removeNameAtIndex(0)

        // Then
        assertTrue(result)
        assertFalse(testee.isProfileComplete)
    }

    @Test
    fun whenInvalidIndexIsUsedToRemoveNameThenReturnsFalse() {
        // When
        val result = testee.removeNameAtIndex(0)

        // Then
        assertFalse(result)
    }

    @Test
    fun whenMultipleAddressesAreRemovedThenIndicesAdjustCorrectly() {
        // Given
        testee.addAddress("New York", "NY")
        testee.addAddress("Los Angeles", "CA")
        testee.addAddress("Chicago", "IL")

        // When
        val result1 = testee.removeAddressAtIndex(1) // Remove LA
        val result2 = testee.removeAddressAtIndex(1) // Remove Chicago (now at index 1)

        // Then
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun whenMultipleNamesAreRemovedThenIndicesAdjustCorrectly() {
        // Given
        testee.addName("John", null, "Doe")
        testee.addName("Jane", null, "Smith")
        testee.addName("Bob", null, "Johnson")

        // When
        val result1 = testee.removeNameAtIndex(1) // Remove Jane
        val result2 = testee.removeNameAtIndex(1) // Remove Bob (now at index 1)

        // Then
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun whenOneNameAndOneAddressExistThenToProfileQueriesReturnsOneProfile() {
        // Given
        val currentYear = 2025
        testee.addName("John", null, "Doe")
        testee.addAddress("New York", "NY")
        testee.setBirthYear(1990)

        // When
        val profiles = testee.toProfileQueries(currentYear)

        // Then
        assertEquals(1, profiles.size)
        with(profiles[0]) {
            assertEquals("John", firstName)
            assertEquals("Doe", lastName)
            assertEquals(null, middleName)
            assertEquals("New York", city)
            assertEquals("NY", state)
            assertEquals(1990, birthYear)
            assertEquals("John Doe", fullName)
            assertEquals(35, age)
            assertEquals(1, addresses.size)
            assertEquals("New York", addresses[0].city)
            assertEquals("NY", addresses[0].state)
            assertFalse(deprecated)
        }
    }

    @Test
    fun whenNameHasMiddleNameThenToProfileQueriesIncludesMiddleNameInFullName() {
        // Given
        val currentYear = 2025
        testee.addName("John", "Michael", "Doe")
        testee.addAddress("New York", "NY")
        testee.setBirthYear(1990)

        // When
        val profiles = testee.toProfileQueries(currentYear)

        // Then
        assertEquals(1, profiles.size)
        with(profiles[0]) {
            assertEquals("John", firstName)
            assertEquals("Michael", middleName)
            assertEquals("Doe", lastName)
            assertEquals("John Michael Doe", fullName)
        }
    }

    @Test
    fun whenMultipleNamesAndAddressesExistThenToProfileQueriesReturnsAllCombinations() {
        // Given
        val currentYear = 2025
        testee.addName("John", null, "Doe")
        testee.addName("Jane", null, "Smith")
        testee.addAddress("New York", "NY")
        testee.addAddress("Los Angeles", "CA")
        testee.setBirthYear(1990)

        // When
        val profiles = testee.toProfileQueries(currentYear)

        // Then
        assertEquals(4, profiles.size) // 2 names × 2 addresses

        // Verify all combinations exist
        val combinations = profiles.map { "${it.firstName} ${it.lastName} - ${it.city}, ${it.state}" }
        assertTrue(combinations.contains("John Doe - New York, NY"))
        assertTrue(combinations.contains("John Doe - Los Angeles, CA"))
        assertTrue(combinations.contains("Jane Smith - New York, NY"))
        assertTrue(combinations.contains("Jane Smith - Los Angeles, CA"))
    }

    @Test
    fun whenNoDataExistsThenToProfileQueriesReturnsEmptyList() {
        // When
        val profiles = testee.toProfileQueries(2025)

        // Then
        assertEquals(0, profiles.size)
    }

    @Test
    fun whenOnlyNamesExistThenToProfileQueriesReturnsEmptyList() {
        // Given
        testee.addName("John", null, "Doe")
        testee.setBirthYear(1990)

        // When
        val profiles = testee.toProfileQueries(2025)

        // Then
        assertEquals(0, profiles.size)
    }

    @Test
    fun whenOnlyAddressesExistThenToProfileQueriesReturnsEmptyList() {
        // Given
        testee.addAddress("New York", "NY")
        testee.setBirthYear(1990)

        // When
        val profiles = testee.toProfileQueries(2025)

        // Then
        assertEquals(0, profiles.size)
    }

    @Test
    fun whenClearIsCalledThenAllDataIsCleared() {
        // Given
        testee.addName("John", null, "Doe")
        testee.addAddress("New York", "NY")
        testee.setBirthYear(1990)
        assertTrue(testee.isProfileComplete)

        // When
        testee.clear()

        // Then
        assertFalse(testee.isProfileComplete)
        assertEquals(0, testee.toProfileQueries(2025).size)
    }

    @Test
    fun whenDataIsAddedAfterClearThenWorksNormally() {
        // Given
        testee.addName("John", null, "Doe")
        testee.clear()

        // When
        val nameResult = testee.addName("Jane", null, "Smith")
        val addressResult = testee.addAddress("Boston", "MA")
        val birthYearResult = testee.setBirthYear(1985)

        // Then
        assertTrue(nameResult)
        assertTrue(addressResult)
        assertTrue(birthYearResult)
        assertTrue(testee.isProfileComplete)
    }

    private fun createProfileQuery(
        firstName: String,
        middleName: String? = null,
        lastName: String,
        city: String,
        state: String,
        birthYear: Int,
    ): ProfileQuery {
        return ProfileQuery(
            id = 1L,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            city = city,
            state = state,
            addresses = listOf(Address(city, state)),
            birthYear = birthYear,
            fullName = if (middleName != null) "$firstName $middleName $lastName" else "$firstName $lastName",
            age = 2025 - birthYear,
            deprecated = false,
        )
    }
}
