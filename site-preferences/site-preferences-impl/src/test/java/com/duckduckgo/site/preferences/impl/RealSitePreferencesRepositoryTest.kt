/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.site.preferences.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.site.preferences.impl.store.SitePreferencesDao
import com.duckduckgo.site.preferences.impl.store.SitePreferencesDatabase
import com.duckduckgo.site.preferences.impl.store.SitePreferencesEntity
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class RealSitePreferencesRepositoryTest {

    @get:Rule @Suppress("unused") val coroutineRule = CoroutineTestRule()

    @get:Rule @Suppress("unused") val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SitePreferencesDatabase
    private lateinit var dao: SitePreferencesDao
    private lateinit var repository: RealSitePreferencesRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SitePreferencesDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.sitePreferencesDao()
        repository = RealSitePreferencesRepository(dao, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)
        repository.onCreate(mock())
    }

    @After fun after() { db.close() }

    @Test
    fun whenDesktopModeRememberedThenIsRememberedReturnsTrue() = runTest {
        assertFalse(repository.isDesktopModeRemembered("https://example.com"))
        repository.rememberDesktopMode("https://example.com")
        assertTrue(repository.isDesktopModeRemembered("https://example.com"))
    }

    @Test
    fun whenDesktopModeForgottenThenIsRememberedReturnsFalse() = runTest {
        repository.rememberDesktopMode("https://example.com")
        assertTrue(repository.isDesktopModeRemembered("https://example.com"))
        repository.forgetDesktopMode("https://example.com")
        assertFalse(repository.isDesktopModeRemembered("https://example.com"))
    }

    @Test
    fun whenUrlVariantsShareSiteKeyThenTreatedAsSameSite() = runTest {
        // m./www./subdomains/paths all collapse to the eTLD+1 site key.
        repository.rememberDesktopMode("https://m.example.com/some/path")
        assertTrue(repository.isDesktopModeRemembered("https://www.example.com"))
        assertTrue(repository.isDesktopModeRememberedInCache("https://news.example.com/article"))
    }

    @Test
    fun whenCachePrimedFromDbThenInCacheReflectsExistingRows() {
        dao.insert(SitePreferencesEntity(domain = "seeded.com", desktopModeEnabled = true))
        assertTrue(repository.isDesktopModeRememberedInCache("https://seeded.com"))
    }

    @Test
    fun whenCacheNotYetPrimedThenIsDesktopModeRememberedStillResolvesValueFromDb() = runTest {
        val unprimed = RealSitePreferencesRepository(dao, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)
        dao.insert(SitePreferencesEntity(domain = "seeded.com", desktopModeEnabled = true))
        assertFalse(unprimed.isDesktopModeRememberedInCache("https://seeded.com"))
        assertTrue(unprimed.isDesktopModeRemembered("https://seeded.com"))
    }

    @Test
    fun whenCachePrimedThenIsDesktopModeRememberedReflectsCache() = runTest {
        repository.rememberDesktopMode("https://example.com")
        assertTrue(repository.isDesktopModeRemembered("https://example.com"))
        assertFalse(repository.isDesktopModeRemembered("https://not-remembered.com"))
    }

    @Test
    fun whenClearAllButFireproofedThenOnlyFireproofedDomainsRetained() = runTest {
        repository.rememberDesktopMode("https://keep.com")
        repository.rememberDesktopMode("https://drop.com")
        repository.clearAllButFireproofed(setOf("keep.com"))
        assertTrue(repository.isDesktopModeRememberedInCache("https://keep.com"))
        assertFalse(repository.isDesktopModeRememberedInCache("https://drop.com"))
    }

    @Test
    fun whenClearAllButFireproofedWithEmptySetThenEverythingForgotten() = runTest {
        repository.rememberDesktopMode("https://a.com")
        repository.rememberDesktopMode("https://b.com")
        repository.clearAllButFireproofed(emptySet())
        assertFalse(repository.isDesktopModeRememberedInCache("https://a.com"))
        assertFalse(repository.isDesktopModeRememberedInCache("https://b.com"))
    }

    @Test
    fun whenClearThenAllGivenDomainsRemoved() = runTest {
        repository.rememberDesktopMode("https://a.com")
        repository.rememberDesktopMode("https://b.com")
        repository.rememberDesktopMode("https://c.com")
        repository.clear(setOf("a.com", "b.com"))
        assertFalse(repository.isDesktopModeRememberedInCache("https://a.com"))
        assertFalse(repository.isDesktopModeRememberedInCache("https://b.com"))
        assertTrue(repository.isDesktopModeRememberedInCache("https://c.com"))
    }
}
