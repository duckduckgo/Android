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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealPirWebOnboardingStateHolderTest {

    private lateinit var testee: RealPirWebOnboardingStateHolder

    @Before
    fun setUp() {
        testee = RealPirWebOnboardingStateHolder()
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
}
