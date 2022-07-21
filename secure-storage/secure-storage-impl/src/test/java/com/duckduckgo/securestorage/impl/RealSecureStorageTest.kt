/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.securestorage.impl

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.securestorage.impl.encryption.EncryptionHelper.EncryptedString
import com.duckduckgo.securestorage.store.SecureStorageRepository
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealSecureStorageTest {
    private lateinit var testee: RealSecureStorage
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()
    @Mock
    private lateinit var secureStorageRepository: SecureStorageRepository
    @Mock
    private lateinit var l2DataTransformer: L2DataTransformer
    private val testCredentials = WebsiteLoginDetailsWithCredentials(
        details = WebsiteLoginDetails(
            domain = "test.com",
            username = "user@test.com",
            id = 1,
            domainTitle = "test",
            notes = "notes",
            lastUpdatedMillis = 1000L
        ),
        password = expectedDecryptedData
    )

    private val testEntity = WebsiteLoginCredentialsEntity(
        id = 1,
        domain = "test.com",
        username = "user@test.com",
        password = expectedEncryptedData,
        iv = expectedEncryptedIv,
        notes = "notes",
        domainTitle = "test",
        lastUpdatedInMillis = 1000L
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(l2DataTransformer.decrypt(any(), any())).thenReturn(expectedDecryptedData)
        whenever(l2DataTransformer.encrypt(any())).thenReturn(
            EncryptedString(expectedEncryptedData, expectedEncryptedIv)
        )
        testee = RealSecureStorage(secureStorageRepository, coroutineRule.testDispatcherProvider, l2DataTransformer)
    }

    @Test
    fun whenCredentialsAddedThenAddEntityToRepositoryWithEncryptedPasswordAndIv() = runTest {
        testee.addWebsiteLoginDetailsWithCredentials(testCredentials)

        verify(secureStorageRepository).addWebsiteLoginCredential(testEntity)
    }

    @Test
    fun whenCanAccessSecureStorageThenReturnCanProcessDataValue() {
        whenever(l2DataTransformer.canProcessData()).thenReturn(true)

        assertTrue(testee.canAccessSecureStorage())
    }

    @Test
    fun whenCredentialsDeletedThenDeleteEntityWithIdFromSecureStorageRepository() = runTest {
        testee.deleteWebsiteLoginDetailsWithCredentials(1)

        verify(secureStorageRepository).deleteWebsiteLoginCredentials(1)
    }

    @Test
    fun whenCredentialsUpdatedThenUpdateEntityInSecureStorageRepository() = runTest {
        testee.updateWebsiteLoginDetailsWithCredentials(testCredentials)

        verify(secureStorageRepository).updateWebsiteLoginCredentials(testEntity)
    }

    @Test
    fun whenGetCredentialsWIthIdThenGetEntityWithIdFromSecureStorageRepository() = runTest {
        whenever(secureStorageRepository.getWebsiteLoginCredentialsForId(1)).thenReturn(testEntity)

        val result = testee.getWebsiteLoginDetailsWithCredentials(1)

        assertEquals(testCredentials, result)
    }

    @Test
    fun whenAllWebsiteLoginDetailsRequestedThenGetAllEntitiesAndReturnAllDetailsOnly() = runTest {
        whenever(secureStorageRepository.websiteLoginCredentials()).thenReturn(
            MutableStateFlow(listOf(testEntity))
        )

        val result = testee.websiteLoginDetails().first()

        assertEquals(listOf(testCredentials.details), result)
    }

    @Test
    fun whenWebsiteLoginDetailsForDomainRequestedThenGetEntityForDomainAndReturnDetailsOnly() = runTest {
        whenever(secureStorageRepository.websiteLoginCredentialsForDomain("test.com")).thenReturn(
            MutableStateFlow(listOf(testEntity))
        )

        val result = testee.websiteLoginDetailsForDomain("test.com").first()

        assertEquals(listOf(testCredentials.details), result)
    }

    @Test
    fun whenAllWebsiteLoginCredentialsRequestedThenGetAllEntitiesAndReturnIncludingDecryptedPassword() = runTest {
        whenever(secureStorageRepository.websiteLoginCredentials()).thenReturn(
            MutableStateFlow(listOf(testEntity))
        )

        val result = testee.websiteLoginDetailsWithCredentials().first()

        assertEquals(listOf(testCredentials), result)
    }

    @Test
    fun whenWebsiteLoginCredentialsForDomainRequestedThenGetEntityForDomainAndReturnIncludingDecryptedPassword() = runTest {
        whenever(secureStorageRepository.websiteLoginCredentialsForDomain("test.com")).thenReturn(
            MutableStateFlow(listOf(testEntity))
        )

        val result = testee.websiteLoginDetailsWithCredentialsForDomain("test.com").first()

        assertEquals(listOf(testCredentials), result)
    }

    companion object {
        private const val expectedEncryptedData: String = "ZXhwZWN0ZWRFbmNyeXB0ZWREYXRh"
        private const val expectedEncryptedIv: String = "ZXhwZWN0ZWRFbmNyeXB0ZWRJVg=="
        private const val expectedDecryptedData: String = "ZXhwZWN0ZWREZWNyeXB0ZWREYXRh"
    }
}
