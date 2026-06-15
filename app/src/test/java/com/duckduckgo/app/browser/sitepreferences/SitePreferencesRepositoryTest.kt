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

package com.duckduckgo.app.browser.sitepreferences

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
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
class SitePreferencesRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: SitePreferencesDao
    private lateinit var repository: RealSitePreferencesRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sitePreferencesDao()
        repository = RealSitePreferencesRepository(dao, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)
        repository.onCreate(mock()) // start the cache-priming collector, as the app does at startup
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDesktopModeRememberedThenIsRememberedReturnsTrue() = runTest {
        assertFalse(repository.isDesktopModeRemembered("example.com"))
        repository.rememberDesktopMode("example.com")
        assertTrue(repository.isDesktopModeRemembered("example.com"))
    }

    @Test
    fun whenDesktopModeForgottenThenIsRememberedReturnsFalse() = runTest {
        repository.rememberDesktopMode("example.com")
        assertTrue(repository.isDesktopModeRemembered("example.com"))
        repository.forgetDesktopMode("example.com")
        assertFalse(repository.isDesktopModeRemembered("example.com"))
    }

    @Test
    fun whenCachePrimedFromDbThenIsRememberedReflectsExistingRows() {
        dao.insert(SitePreferencesEntity(domain = "seeded.com", desktopModeEnabled = true))
        assertTrue(repository.isDesktopModeRemembered("seeded.com"))
    }

    @Test
    fun whenClearAllButFireproofedThenOnlyFireproofedDomainsRetained() = runTest {
        repository.rememberDesktopMode("keep.com")
        repository.rememberDesktopMode("drop.com")

        repository.clearAllButFireproofed(setOf("keep.com"))

        assertTrue(repository.isDesktopModeRemembered("keep.com"))
        assertFalse(repository.isDesktopModeRemembered("drop.com"))
    }

    @Test
    fun whenClearAllButFireproofedWithEmptySetThenEverythingForgotten() = runTest {
        // The common full-fire case: no fireproofed sites. Exercises the empty `NOT IN ()` query path.
        repository.rememberDesktopMode("a.com")
        repository.rememberDesktopMode("b.com")

        repository.clearAllButFireproofed(emptySet())

        assertFalse(repository.isDesktopModeRemembered("a.com"))
        assertFalse(repository.isDesktopModeRemembered("b.com"))
    }

    @Test
    fun whenBulkForgetThenAllGivenDomainsRemoved() = runTest {
        repository.rememberDesktopMode("a.com")
        repository.rememberDesktopMode("b.com")
        repository.rememberDesktopMode("c.com")

        repository.forgetDesktopMode(setOf("a.com", "b.com"))

        assertFalse(repository.isDesktopModeRemembered("a.com"))
        assertFalse(repository.isDesktopModeRemembered("b.com"))
        assertTrue(repository.isDesktopModeRemembered("c.com"))
    }
}
