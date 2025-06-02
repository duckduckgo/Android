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

package com.duckduckgo.autofill.com.duckduckgo.autofill.store

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.autofill.store.db.NeverSavedSiteEntity
import com.duckduckgo.autofill.store.db.NeverSavedSitesDao
import com.duckduckgo.autofill.store.db.SecureStorageDatabase
import com.duckduckgo.autofill.store.db.WebsiteLoginCredentialsDao
import com.duckduckgo.autofill.store.db.WebsiteLoginCredentialsEntity
import com.duckduckgo.common.test.CoroutineTestRule
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

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealSecureStorageRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: SecureStorageDatabase

    private lateinit var websiteLoginCredentialsDao: WebsiteLoginCredentialsDao
    private lateinit var neverSavedSitesDao: NeverSavedSitesDao
    private lateinit var testee: com.duckduckgo.autofill.store.RealSecureStorageRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SecureStorageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        websiteLoginCredentialsDao = db.websiteLoginCredentialsDao()
        neverSavedSitesDao = db.neverSavedSitesDao()
        testee = com.duckduckgo.autofill.store.RealSecureStorageRepository(
            websiteLoginCredentialsDao,
            neverSavedSitesDao,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenRetrievingLoginCredentialByIdThenNullReturnedIfNotExists() = runTest {
        assertNull(websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(entity().id))
    }

    @Test
    fun whenRetrievingLoginCredentialByIdThenReturnedIfExists() = runTest {
        val testEntity = entity()
        websiteLoginCredentialsDao.insert(entity())
        val result = testee.getWebsiteLoginCredentialsForId(testEntity.id)
        assertEquals(testEntity, result)
    }

    @Test
    fun whenRetrievingLoginCredentialByDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity()
        websiteLoginCredentialsDao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("test.com").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByEmptyDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity().copy(domain = "")
        websiteLoginCredentialsDao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByNullDomainThenReturnedIfDirectMatch() = runTest {
        val testEntity = entity().copy(domain = null)
        websiteLoginCredentialsDao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentialsForDomain("").first()
        assertEquals(testEntity, result[0])
    }

    @Test
    fun whenRetrievingLoginCredentialByDomainThenEmptyListReturnedIfNoMatches() = runTest {
        val testEntity = entity()
        websiteLoginCredentialsDao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> =
            testee.websiteLoginCredentialsForDomain("no-matches.com").first()
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
        websiteLoginCredentialsDao.insert(testEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentials().first()
        assertEquals(listOf(testEntity), result)
    }

    @Test
    fun whenGetAllWebsiteLoginCredentialsWithMultipleSitesThenThatAllReturned() = runTest {
        val testEntity = entity()
        val anotherEntity = entity(id = testEntity.id + 1)
        websiteLoginCredentialsDao.insert(testEntity)
        websiteLoginCredentialsDao.insert(anotherEntity)
        val result: List<WebsiteLoginCredentialsEntity> = testee.websiteLoginCredentials().first()
        assertEquals(listOf(testEntity, anotherEntity), result)
    }

    @Test
    fun whenUpdateWebsiteLoginCredentialsThenCallUpdateToDao() = runTest {
        val testEntity = entity()
        websiteLoginCredentialsDao.insert(testEntity)
        testee.updateWebsiteLoginCredentials(testEntity.copy(username = "newUsername"))
        val updated = testee.getWebsiteLoginCredentialsForId(testEntity.id)
        assertNotNull(updated)
        assertEquals("newUsername", updated!!.username)
    }

    @Test
    fun whenDeleteWebsiteLoginCredentialsThenEntityRemoved() = runTest {
        val testEntity = entity()
        websiteLoginCredentialsDao.insert(testEntity)
        testee.deleteWebsiteLoginCredentials(1)
        val nowDeleted = websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(testEntity.id)
        assertNull(nowDeleted)
    }

    @Test
    fun whenNoSitesEverAddedToNeverSaveListThenCountIs0() = runTest {
        testee.neverSaveListCount().test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNeverSavedSiteAddedThenCountIncreases() = runTest {
        testee.neverSaveListCount().test {
            assertEquals(0, awaitItem())
            neverSavedSitesDao.insert(NeverSavedSiteEntity(domain = "test.com"))
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNeverSavedSiteAddedButExactDomainAlreadyInDbThenCountDoesNotIncrease() = runTest {
        testee.neverSaveListCount().test {
            // starts at count = 0
            assertEquals(0, awaitItem())

            // should increase count to 1
            neverSavedSitesDao.insert(NeverSavedSiteEntity(domain = "test.com"))
            assertEquals(1, awaitItem())

            // should not increase count
            neverSavedSitesDao.insert(NeverSavedSiteEntity(domain = "test.com"))
            cancelAndConsumeRemainingEvents()
        }

        assertEquals(1, testee.neverSaveListCount().first())
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
        lastUsedInMillis: Long = 0L,
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
            lastUsedInMillis = lastUsedInMillis,
        )
    }
}
