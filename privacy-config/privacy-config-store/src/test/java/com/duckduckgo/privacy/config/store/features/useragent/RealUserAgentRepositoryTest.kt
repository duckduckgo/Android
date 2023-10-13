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

package com.duckduckgo.privacy.config.store.features.useragent

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.UserAgentSitesEntity
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity
import com.duckduckgo.privacy.config.store.toFeatureException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealUserAgentRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealUserAgentRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockUserAgentDao: UserAgentDao = mock()
    private val mockUserAgentSitesDao: UserAgentSitesDao = mock()
    private val mockUserAgentStatesDao: UserAgentStatesDao = mock()
    private val mockUserAgentVersionsDao: UserAgentVersionsDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.userAgentDao()).thenReturn(mockUserAgentDao)
        whenever(mockDatabase.userAgentSitesDao()).thenReturn(mockUserAgentSitesDao)
        whenever(mockDatabase.userAgentStatesDao()).thenReturn(mockUserAgentStatesDao)
        whenever(mockDatabase.userAgentVersionsDao()).thenReturn(mockUserAgentVersionsDao)
        testee =
            RealUserAgentRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
            )
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenUserAgentDaoContainsExceptions()
        val actual = userAgentException.toFeatureException()
        testee =
            RealUserAgentRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
            )

        assertEquals(testee.omitApplicationExceptions.first(), actual)
        assertEquals(testee.omitVersionExceptions.first(), actual)
        assertEquals(testee.defaultExceptions.first(), actual)
    }

    @Test
    fun whenRepositoryIsCreatedThenUserAgentConfigLoadedIntoMemory() {
        givenUserAgentDaoContainsConfig()
        testee =
            RealUserAgentRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
            )

        assertEquals(testee.closestUserAgentState, true)
        assertEquals(testee.ddgFixedUserAgentState, true)
        assertEquals(testee.ddgDefaultSites.first(), userAgentDefaultSiteEntity.toFeatureException())
        assertEquals(testee.ddgFixedSites.first(), userAgentFixedSiteEntity.toFeatureException())
        assertEquals(testee.closestUserAgentVersions.first(), userAgentClosestVersionEntity.version)
        assertEquals(testee.ddgFixedUserAgentVersions.first(), userAgentDdgFixedVersionEntity.version)
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealUserAgentRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            testee.updateAll(listOf(), listOf(), anyOrNull(), listOf())

            verify(mockUserAgentDao).updateAll(anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenUserAgentDaoContainsExceptions()
            testee =
                RealUserAgentRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )
            assertEquals(1, testee.defaultExceptions.size)
            assertEquals(1, testee.omitApplicationExceptions.size)
            assertEquals(1, testee.omitVersionExceptions.size)
            reset(mockUserAgentDao)

            testee.updateAll(listOf(), listOf(), anyOrNull(), listOf())

            assertEquals(0, testee.defaultExceptions.size)
            assertEquals(0, testee.omitApplicationExceptions.size)
            assertEquals(0, testee.omitVersionExceptions.size)
        }

    @Test
    fun whenUpdateAllThenPreviousConfigIsCleared() =
        runTest {
            givenUserAgentDaoContainsConfig()
            testee =
                RealUserAgentRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )
            assertEquals(true, testee.closestUserAgentState)
            assertEquals(true, testee.ddgFixedUserAgentState)
            assertEquals("ddgFixed", testee.defaultPolicy)
            assertEquals(1, testee.ddgDefaultSites.size)
            assertEquals(1, testee.ddgFixedSites.size)
            assertEquals(1, testee.closestUserAgentVersions.size)
            assertEquals(1, testee.ddgFixedUserAgentVersions.size)
            reset(mockUserAgentStatesDao)
            reset(mockUserAgentSitesDao)
            reset(mockUserAgentVersionsDao)

            testee.updateAll(listOf(), listOf(), null, listOf())

            assertEquals(false, testee.closestUserAgentState)
            assertEquals(false, testee.ddgFixedUserAgentState)
            assertEquals("ddg", testee.defaultPolicy)
            assertEquals(0, testee.ddgDefaultSites.size)
            assertEquals(0, testee.ddgFixedSites.size)
            assertEquals(0, testee.closestUserAgentVersions.size)
            assertEquals(0, testee.ddgFixedUserAgentVersions.size)
        }

    private fun givenUserAgentDaoContainsExceptions() {
        whenever(mockUserAgentDao.getApplicationExceptions()).thenReturn(listOf(userAgentException))
        whenever(mockUserAgentDao.getDefaultExceptions()).thenReturn(listOf(userAgentException))
        whenever(mockUserAgentDao.getVersionExceptions()).thenReturn(listOf(userAgentException))
    }

    private fun givenUserAgentDaoContainsConfig() {
        whenever(mockUserAgentStatesDao.get()).thenReturn(userAgentStatesEntity)
        whenever(mockUserAgentSitesDao.getDefaultSites()).thenReturn(listOf(userAgentDefaultSiteEntity))
        whenever(mockUserAgentSitesDao.getFixedSites()).thenReturn(listOf(userAgentFixedSiteEntity))
        whenever(mockUserAgentVersionsDao.getClosestUserAgentVersions()).thenReturn(listOf(userAgentClosestVersionEntity))
        whenever(mockUserAgentVersionsDao.getDdgFixedUserAgentVerions()).thenReturn(listOf(userAgentDdgFixedVersionEntity))
    }

    companion object {
        val userAgentException = UserAgentExceptionEntity("example.com", "reason", omitApplication = false, omitVersion = false)
        val userAgentStatesEntity = UserAgentStatesEntity(defaultPolicy = "ddgFixed", closestUserAgent = true, ddgFixedUserAgent = true)
        val userAgentDefaultSiteEntity = UserAgentSitesEntity("example.com", "reason", ddgDefaultSite = true, ddgFixedSite = false)
        val userAgentFixedSiteEntity = UserAgentSitesEntity("example.com", "reason", ddgDefaultSite = false, ddgFixedSite = true)
        val userAgentClosestVersionEntity = UserAgentVersionsEntity("123", closestUserAgent = true, ddgFixedUserAgent = false)
        val userAgentDdgFixedVersionEntity = UserAgentVersionsEntity("456", closestUserAgent = false, ddgFixedUserAgent = true)
    }
}
