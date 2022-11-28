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

package com.duckduckgo.securestorage.store

import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsDao
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealSecureStorageRepositoryTest {
    @Mock
    private lateinit var dao: WebsiteLoginCredentialsDao
    private lateinit var testee: RealSecureStorageRepository
    private val testEntity = WebsiteLoginCredentialsEntity(
        id = 1,
        domain = "test.com",
        username = "test",
        password = "pass123",
        passwordIv = "iv",
        notes = "my notes",
        notesIv = "notesIv",
        domainTitle = "test",
        lastUpdatedInMillis = 0L,
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealSecureStorageRepository(dao)
    }

    @Test
    fun whenAddWebsiteLoginCredentialThenCallInsertToDao() {
        runTest {
            testee.addWebsiteLoginCredential(testEntity)
        }

        verify(dao).insert(testEntity)
    }

    @Test
    fun whenGetWebsiteLoginCredentialsWithDomainThenCallGetWithDomainFromDao() = runTest {
        whenever(dao.websiteLoginCredentialsByDomain("test")).thenReturn(
            MutableStateFlow(listOf(testEntity)),
        )

        val result: List<WebsiteLoginCredentialsEntity> =
            testee.websiteLoginCredentialsForDomain("test").first()

        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenGetAllWebsiteLoginCredentialsThenCallGetAllFromDao() = runTest {
        whenever(dao.websiteLoginCredentials()).thenReturn(
            MutableStateFlow(listOf(testEntity)),
        )

        val result: List<WebsiteLoginCredentialsEntity> =
            testee.websiteLoginCredentials().first()

        assertEquals(listOf(testEntity), result)
    }

    @Test
    fun whenGetWebsiteLoginCredentialsWithIDThenCallGetWithIDFromDao() = runTest {
        whenever(dao.getWebsiteLoginCredentialsById(1)).thenReturn(testEntity)

        val result =
            testee.getWebsiteLoginCredentialsForId(1)

        assertEquals(testEntity, result)
    }

    @Test
    fun whenUpdateWebsiteLoginCredentialsThenCallUpdateToDao() = runTest {
        testee.updateWebsiteLoginCredentials(testEntity)

        verify(dao).update(testEntity)
    }

    @Test
    fun whenDeleteWebsiteLoginCredentialsThenCallDeleteToDao() = runTest {
        testee.deleteWebsiteLoginCredentials(1)

        verify(dao).delete(1)
    }
}
