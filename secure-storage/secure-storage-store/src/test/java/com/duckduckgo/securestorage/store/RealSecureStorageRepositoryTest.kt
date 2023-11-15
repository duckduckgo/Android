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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.securestorage.store.db.SecureStorageDatabase
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsDao
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.robolectric.RuntimeEnvironment

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RealSecureStorageRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SecureStorageDatabase

    private lateinit var dao: WebsiteLoginCredentialsDao
    private lateinit var testee: RealSecureStorageRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SecureStorageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.websiteLoginCredentialsDao()
        testee = RealSecureStorageRepository(dao)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenRetrievingLoginCredentialByIdThenNullReturnedIfNotExists() = runTest {
        assertNull(dao.getWebsiteLoginCredentialsById(entity().id))
    }

    @Test
    fun whenRetrievingLoginCredentialByIdThenReturnedIfExists() = runTest {
        val testEntity = entity()
        dao.insert(entity())
        val result = testee.getWebsiteLoginCredentialsForId(testEntity.id)
        assertEquals(testEntity, result)
    }

    @Test
    fun whenRetrievingLoginCredentialByDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity()
        dao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("test.com").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByEmptyDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity().copy(domain = "")
        dao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByNullDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity().copy(domain = null)
        dao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByDomainThenEmptyListReturnedIfNoMatches() = runTest {
        val testEntity = entity()
        dao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("no-matches.com").first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllWebsiteLoginCredentialsWithSitesThenEmptyListReturned() = runTest {
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentials().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllWebsiteLoginCredentialsWithASingleSiteThenThatOneIsReturned() = runTest {
        val testEntity = entity()
        dao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentials().first()
        assertEquals(listOf(testEntity), result)
    }

    @Test
    fun whenGetAllWebsiteLoginCredentialsWithMultipleSitesThenThatAllReturned() = runTest {
        val testEntity = entity()
        val anotherEntity = entity(id = testEntity.id + 1)
        dao.insert(testEntity)
        dao.insert(anotherEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentials().first()
        assertEquals(listOf(testEntity, anotherEntity), result)
    }

    @Test
    fun whenUpdateWebsiteLoginCredentialsThenCallUpdateToDao() = runTest {
        val testEntity = entity()
        dao.insert(testEntity)
        testee.updateWebsiteLoginCredentials(testEntity.copy(username = "newUsername"))
        val updated = testee.getWebsiteLoginCredentialsForId(testEntity.id)
        assertNotNull(updated)
        assertEquals("newUsername", updated!!.username)
    }

    @Test
    fun whenDeleteWebsiteLoginCredentialsThenEntityRemoved() = runTest {
        val testEntity = entity()
        dao.insert(testEntity)
        testee.deleteWebsiteLoginCredentials(1)
        val nowDeleted = dao.getWebsiteLoginCredentialsById(testEntity.id)
        assertNull(nowDeleted)
    }

    private fun entity(
        id: Long = 1,
        domain: String = "test.com",
        username: String = "test",
        password: String = "pass123",
        passwordIv: String = "iv",
        notes: String = "my notes",
        notesIv: String = "notesIv",
        domainTitle: String = "test",
        lastUpdatedInMillis: Long = 0L,
    ): WebsiteLoginCredentialsEntity {
        return WebsiteLoginCredentialsEntity(
            id = id,
            domain = domain,
            username = username,
            password = password,
            passwordIv = passwordIv,
            notes = notes,
            notesIv = notesIv,
            domainTitle = domainTitle,
            lastUpdatedInMillis = lastUpdatedInMillis,
        )
    }
}
