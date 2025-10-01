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

package com.duckduckgo.app.browser.indexeddb

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class IndexedDBManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockFeature: AndroidBrowserConfigFeature = mock()
    private val mockIndexedDBToggle: Toggle = mock()
    private val mockFireproofToggle: Toggle = mock()
    private val mockFireproofRepository: FireproofWebsiteRepository = mock()
    private val mockFileDeleter: FileDeleter = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val moshi: Moshi = Moshi.Builder().build()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    private val testee by lazy {
        DuckDuckGoIndexedDBManager(
            context,
            mockFeature,
            mockFireproofRepository,
            mockFileDeleter,
            moshi,
            dispatcherProvider,
            mockSettingsDataStore,
        )
    }

    @Before
    fun setup() {
        File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB").apply { mkdirs() }

        whenever(mockFeature.indexedDB()).thenReturn(mockIndexedDBToggle)
        whenever(mockFeature.fireproofedIndexedDB()).thenReturn(mockFireproofToggle)
    }

    @Test
    fun whenClearIndexedDBWithNoExclusionsThenDeleteContents() = runTest {
        testee.clearIndexedDB()

        verify(mockFileDeleter).deleteContents(
            eq(File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")),
            eq(emptyList()),
        )
    }

    @Test
    fun whenSettingsHasDomainsAndFireproofIndexedDBIsDisabledThenExcludeMatchingFolders() = runTest {
        val json = """{"domains":["example.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_example.com_0.indexeddb.leveldb",
            "https_foo.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB()

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(listOf("https_example.com_0.indexeddb.leveldb")),
        )
    }

    @Test
    fun whenSettingsHasDomainsAndHasFireproofedDomainsThenExcludeMatchingFolders() = runTest {
        val json = """{"domains":["example.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(true)
        whenever(mockFireproofRepository.fireproofWebsitesSync()).thenReturn(listOf(FireproofWebsiteEntity(domain = "foo.com")))

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_example.com_0.indexeddb.leveldb",
            "https_foo.com_0.indexeddb.leveldb",
            "https_bar.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB()

        verify(mockFireproofRepository).fireproofWebsitesSync()
        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            argThat { list ->
                list.toSet() == setOf(
                    "https_example.com_0.indexeddb.leveldb",
                    "https_foo.com_0.indexeddb.leveldb",
                )
            },
        )
    }

    @Test
    fun whenSettingsHasNoDomainsAndHasFireproofedDomainsThenExcludeMatchingFolders() = runTest {
        val json = """{"domains":[]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(true)
        whenever(mockFireproofRepository.fireproofWebsitesSync()).thenReturn(listOf(FireproofWebsiteEntity(domain = "foo.com")))

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_foo.com_0.indexeddb.leveldb",
            "https_bar.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB()

        verify(mockFireproofRepository).fireproofWebsitesSync()
        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(
                listOf(
                    "https_foo.com_0.indexeddb.leveldb",
                ),
            ),
        )
    }

    @Test
    fun whenSettingsHasDomainsThenExcludeSubdomainFolders() = runTest {
        val json = """{"domains":["example.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_subdomain.example.com_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
            "https_other.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB()

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            argThat { list ->
                list.toSet() == setOf(
                    "https_subdomain.example.com_0.indexeddb.leveldb",
                    "https_example.com_0.indexeddb.leveldb",
                )
            },
        )
    }

    @Test
    fun whenFireproofedIndexDBDisabledThenDoNotFetchFireproofWebsites() = runTest {
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        testee.clearIndexedDB()

        verify(mockFireproofRepository, never()).fireproofWebsitesSync()
    }
}
