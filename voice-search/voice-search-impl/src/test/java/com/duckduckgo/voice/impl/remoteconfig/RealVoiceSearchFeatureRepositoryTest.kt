/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.voice.impl.remoteconfig

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.voice.store.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealVoiceSearchFeatureRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDatabase: VoiceSearchDatabase = mock()
    private val mockDao: VoiceSearchDao = mock()

    private lateinit var repository: RealVoiceSearchFeatureRepository

    @Before
    fun setUp() {
        whenever(mockDatabase.voiceSearchDao()).thenReturn(mockDao)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() = runTest {
        givenDaoContainsExceptions()

        repository = RealVoiceSearchFeatureRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)

        assertEquals(1, repository.manufacturerExceptions.size)
        assertEquals("manufacturer", repository.manufacturerExceptions.first().name)

        assertEquals(1, repository.localeExceptions.size)
        assertEquals("locale", repository.localeExceptions.first().name)

        assertEquals(123, repository.minVersion)
    }

    @Test
    fun whenUpdateAllExceptionsIsCalledThenDatabaseIsUpdated() = runTest {
        repository = RealVoiceSearchFeatureRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)

        val manufacturerExceptions = listOf(Manufacturer("manufacturer"))
        val localeExceptions = listOf(Locale("locale"))
        val minVersion = 123

        repository.updateAllExceptions(manufacturerExceptions, localeExceptions, minVersion)

        verify(mockDao).updateAll(
            manufacturerExceptions.map { ManufacturerEntity(it.name) },
            localeExceptions.map { LocaleEntity(it.name) },
            MinVersionEntity(minVersion),
        )
    }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() = runTest {
        givenDaoContainsExceptions()
        repository = RealVoiceSearchFeatureRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)

        assertEquals(1, repository.manufacturerExceptions.size)
        assertEquals(1, repository.localeExceptions.size)
        assertEquals(123, repository.minVersion)

        reset(mockDao)

        repository.updateAllExceptions(listOf(), listOf(), 0)

        assertEquals(0, repository.manufacturerExceptions.size)
        assertEquals(0, repository.localeExceptions.size)
        assertNull(repository.minVersion)
    }

    private fun givenDaoContainsExceptions() {
        whenever(mockDao.getManufacturerExceptions()).thenReturn(listOf(ManufacturerEntity("manufacturer")))
        whenever(mockDao.getLocaleExceptions()).thenReturn(listOf(LocaleEntity("locale")))
        whenever(mockDao.getMinVersion()).thenReturn(MinVersionEntity(123))
    }
}
