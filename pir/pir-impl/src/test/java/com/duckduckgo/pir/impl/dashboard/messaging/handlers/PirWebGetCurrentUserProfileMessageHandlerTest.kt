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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebGetCurrentUserProfileMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebGetCurrentUserProfileMessageHandler

    private val mockRepository: PirRepository = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()
    private val mockPirWebProfileStateHolder: PirWebProfileStateHolder = mock()

    @Before
    fun setUp() {
        testee = PirWebGetCurrentUserProfileMessageHandler(
            repository = mockRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
            pirWebProfileStateHolder = mockPirWebProfileStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.GET_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithNoProfilesThenSendsDefaultSuccessResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(emptyList())
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithSingleProfileThenSendsProfileResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val profileQuery = createProfileQuery(
            firstName = "John",
            middleName = "Michael",
            lastName = "Doe",
            birthYear = 1990,
            addresses = listOf(Address("New York", "NY")),
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQuery))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profileQuery))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("John", "Michael", "Doe")),
            expectedAddresses = listOf(ExpectedAddress("New York", "NY")),
            expectedBirthYear = 1990,
        )
    }

    @Test
    fun whenProcessWithMultipleProfilesSameNameThenDeduplicatesNames() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val address1 = Address("New York", "NY")
        val address2 = Address("Los Angeles", "CA")
        val profile1 = createProfileQuery("John", "Michael", "Doe", 1990, listOf(address1))
        val profile2 = createProfileQuery("John", "Michael", "Doe", 1990, listOf(address2))
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profile1, profile2))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profile1, profile2))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("John", "Michael", "Doe")),
            expectedAddresses = listOf(
                ExpectedAddress("New York", "NY"),
                ExpectedAddress("Los Angeles", "CA"),
            ),
            expectedBirthYear = 1990,
        )
    }

    @Test
    fun whenProcessWithMultipleProfilesDifferentNamesThenIncludesAllNames() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val address = Address("Chicago", "IL")
        val profile1 = createProfileQuery("John", "Michael", "Doe", 1990, listOf(address))
        val profile2 = createProfileQuery("Jane", null, "Smith", 1990, listOf(address))
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profile1, profile2))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profile1, profile2))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(
                ExpectedName("John", "Michael", "Doe"),
                ExpectedName("Jane", "", "Smith"),
            ),
            expectedAddresses = listOf(ExpectedAddress("Chicago", "IL")),
            expectedBirthYear = 1990,
        )
    }

    @Test
    fun whenProcessWithProfileWithNullMiddleNameThenHandlesCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val profileQuery = createProfileQuery(
            firstName = "Jane",
            middleName = null,
            lastName = "Smith",
            birthYear = 1985,
            addresses = listOf(Address("Boston", "MA")),
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQuery))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profileQuery))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("Jane", "", "Smith")),
            expectedAddresses = listOf(ExpectedAddress("Boston", "MA")),
            expectedBirthYear = 1985,
        )
    }

    @Test
    fun whenProcessWithMultipleAddressesThenDeduplicatesAddresses() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val address1 = Address("Seattle", "WA")
        val address2 = Address("Seattle", "WA") // Duplicate
        val address3 = Address("Portland", "OR")
        val profile =
            createProfileQuery("Bob", null, "Johnson", 1975, listOf(address1, address2, address3))
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profile))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profile))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("Bob", "", "Johnson")),
            expectedAddresses = listOf(
                ExpectedAddress("Seattle", "WA"),
                ExpectedAddress("Portland", "OR"),
            ),
            expectedBirthYear = 1975,
        )
    }

    @Test
    fun whenProcessWithProfileWithZeroBirthYearThenReturnsZero() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val profileQuery =
            createProfileQuery("Test", null, "User", 0, listOf(Address("City", "ST")))
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQuery))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(profileQuery))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("Test", "", "User")),
            expectedAddresses = listOf(ExpectedAddress("City", "ST")),
            expectedBirthYear = 0,
        )
    }

    @Test
    fun whenProcessWithProfilesThenFiltersThemOut() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.GET_CURRENT_USER_PROFILE)
        val activeProfile = createProfileQuery("Active", null, "User", 1990, listOf(Address("Active City", "AC")))

        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(activeProfile))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).getValidUserProfileQueries()
        verify(mockPirWebProfileStateHolder).setLoadedProfileQueries(listOf(activeProfile))
        verifyProfileResponse(
            jsMessage,
            expectedNames = listOf(ExpectedName("Active", "", "User")),
            expectedAddresses = listOf(ExpectedAddress("Active City", "AC")),
            expectedBirthYear = 1990,
        )
    }

    private fun createProfileQuery(
        firstName: String,
        middleName: String?,
        lastName: String,
        birthYear: Int,
        addresses: List<Address>,
        deprecated: Boolean = false,
    ): ProfileQuery {
        return ProfileQuery(
            id = 1,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            city = addresses.firstOrNull()?.city ?: "",
            state = addresses.firstOrNull()?.state ?: "",
            addresses = addresses,
            birthYear = birthYear,
            fullName = if (middleName != null) "$firstName $middleName $lastName" else "$firstName $lastName",
            age = 2025 - birthYear,
            deprecated = deprecated,
        )
    }

    private fun verifyProfileResponse(
        jsMessage: JsMessage,
        expectedNames: List<ExpectedName>,
        expectedAddresses: List<ExpectedAddress>,
        expectedBirthYear: Int,
    ) {
        val callbackDataCaptor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())

        val callbackData = callbackDataCaptor.firstValue
        assertEquals(jsMessage.featureName, callbackData.featureName)
        assertEquals(jsMessage.method, callbackData.method)
        assertEquals(jsMessage.id ?: "", callbackData.id)

        // Verify this is a profile response with expected data
        assertTrue(callbackData.params.has("names"))
        assertTrue(callbackData.params.has("addresses"))
        assertTrue(callbackData.params.has("birthYear"))

        val names = callbackData.params.getJSONArray("names")
        val addresses = callbackData.params.getJSONArray("addresses")

        assertEquals(expectedNames.size, names.length())
        assertEquals(expectedAddresses.size, addresses.length())
        assertEquals(expectedBirthYear, callbackData.params.getInt("birthYear"))

        // Verify actual name content
        val actualNames = mutableListOf<ExpectedName>()
        for (i in 0 until names.length()) {
            val nameObject = names.getJSONObject(i)
            actualNames.add(
                ExpectedName(
                    first = nameObject.getString("first"),
                    middle = nameObject.getString("middle"),
                    last = nameObject.getString("last"),
                ),
            )
        }
        assertEquals(expectedNames.toSet(), actualNames.toSet())

        // Verify actual address content
        val actualAddresses = mutableListOf<ExpectedAddress>()
        for (i in 0 until addresses.length()) {
            val addressObject = addresses.getJSONObject(i)
            actualAddresses.add(
                ExpectedAddress(
                    city = addressObject.getString("city"),
                    state = addressObject.getString("state"),
                ),
            )
        }
        assertEquals(expectedAddresses.toSet(), actualAddresses.toSet())
    }

    private data class ExpectedName(
        val first: String,
        val middle: String,
        val last: String,
    )

    private data class ExpectedAddress(
        val city: String,
        val state: String,
    )
}
