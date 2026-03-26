/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.repository

import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.service.DuckAddressStatusManagementService
import com.duckduckgo.autofill.impl.email.service.DuckAddressStatusManagementService.DuckAddressGetStatusResponse
import com.duckduckgo.autofill.impl.ui.credential.repository.DuckAddressStatusRepository.ActivationStatusResult.*
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class RemoteDuckAddressStatusRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val emailManager: EmailManager = mock()
    private val service: DuckAddressStatusManagementService = mock()

    private val testee = RemoteDuckAddressStatusRepository(service, emailManager, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenNotSignedIntoEmailProtectionThenReturnTypeIsNotSignedIn() = runTest {
        configureEmailProtectionNotSignedIn()
        val status = testee.getActivationStatus("foo@example.com")
        assertTrue(status is NotSignedIn)
    }

    @Test
    fun whenStatusIsDeactivatedThenReturnTypeIsDeactivated() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseActivatedIsFalse()
        val status = testee.getActivationStatus("foo@example.com")
        assertTrue(status is Deactivated)
    }

    @Test
    fun whenStatusIsActivatedThenReturnTypeIsActivated() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseActivatedIsTrue()
        val status = testee.getActivationStatus("foo@example.com")
        assertTrue(status is Activated)
    }

    @Test
    fun whenStatusRequestFailsGeneralErrorActivatedThenReturnTypeIsError() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseActivatedIsGeneralError()
        val status = testee.getActivationStatus("foo@example.com")
        assertTrue(status is GeneralError)
    }

    @Test
    fun whenStatusRequestFails404ActivatedThenReturnTypeIsUnmanageable() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseDuckAddressUnmanageable()
        val status = testee.getActivationStatus("foo@example.com")
        assertTrue(status is Unmanageable)
    }

    @Test
    fun whenNotSignedAndTryingToUpdateStatusThenReturnFalse() = runTest {
        configureEmailProtectionNotSignedIn()
        assertFalse(testee.setActivationStatus("foo@example.com", true))
    }

    @Test
    fun whenUpdatingStatusToActivatedAndNewUpdateMatchesThenReturnTrue() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseAfterSettingStatusToBeActivated()
        assertTrue(testee.setActivationStatus("foo@example.com", true))
    }

    @Test
    fun whenUpdatingStatusToActivatedAndNewUpdateDoesNotMatchThenReturnFalse() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseAfterSettingStatusToBeDeactivated()
        assertFalse(testee.setActivationStatus("foo@example.com", true))
    }

    @Test
    fun whenUpdatingStatusToDeactivatedAndNewUpdateMatchesThenReturnTrue() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseAfterSettingStatusToBeDeactivated()
        assertTrue(testee.setActivationStatus("foo@example.com", false))
    }

    @Test
    fun whenUpdatingStatusAndNewUpdateDoesNotMatchThenReturnFalse() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseAfterSettingStatusToBeActivated()
        assertFalse(testee.setActivationStatus("foo@example.com", false))
    }

    @Test
    fun whenUpdatingStatusErrorsThenReturnFalse() = runTest {
        configureEmailProtectionSignedIn()
        configureNetworkResponseToUpdatingSettingsToError()
        assertFalse(testee.setActivationStatus("foo@example.com", false))
    }

    private suspend fun configureNetworkResponseActivatedIsTrue() {
        whenever(service.getActivationStatus(any(), any())).thenReturn(DuckAddressGetStatusResponse(true))
    }

    private suspend fun configureNetworkResponseActivatedIsFalse() {
        whenever(service.getActivationStatus(any(), any())).thenReturn(DuckAddressGetStatusResponse(false))
    }

    private suspend fun configureNetworkResponseActivatedIsGeneralError() {
        whenever(service.getActivationStatus(any(), any())).thenAnswer { throw IOException() }
    }

    private suspend fun configureNetworkResponseDuckAddressUnmanageable() {
        val response: Response<Void> = Response.error(404, "Not Found".toResponseBody(null))
        whenever(service.getActivationStatus(any(), any())).thenAnswer { throw HttpException(response) }
    }

    private suspend fun configureNetworkResponseAfterSettingStatusToBeActivated() {
        whenever(service.setActivationStatus(any(), any(), any())).thenReturn(DuckAddressGetStatusResponse(true))
    }

    private suspend fun configureNetworkResponseAfterSettingStatusToBeDeactivated() {
        whenever(service.setActivationStatus(any(), any(), any())).thenReturn(DuckAddressGetStatusResponse(false))
    }

    private suspend fun configureNetworkResponseToUpdatingSettingsToError() {
        whenever(service.getActivationStatus(any(), any())).thenAnswer { throw IOException() }
    }

    private fun configureEmailProtectionNotSignedIn() = whenever(emailManager.getToken()).thenReturn(null)
    private fun configureEmailProtectionSignedIn() = whenever(emailManager.getToken()).thenReturn("abc123")
}
