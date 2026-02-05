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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
        )
    }

    @Before
    fun setup() {
        File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB").apply { mkdirs() }

        whenever(mockFeature.indexedDB()).thenReturn(mockIndexedDBToggle)
        whenever(mockFeature.fireproofedIndexedDB()).thenReturn(mockFireproofToggle)
        runBlocking {
            whenever(mockFileDeleter.deleteContents(any(), any())).thenReturn(Result.success(Unit))
        }
    }

    @Test
    fun whenClearIndexedDBWithNoExclusionsThenDeleteContents() = runTest {
        testee.clearIndexedDB(shouldClearDuckAiData = false)

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

        testee.clearIndexedDB(shouldClearDuckAiData = false)

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

        testee.clearIndexedDB(shouldClearDuckAiData = false)

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

        testee.clearIndexedDB(shouldClearDuckAiData = false)

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

        testee.clearIndexedDB(shouldClearDuckAiData = false)

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

        testee.clearIndexedDB(shouldClearDuckAiData = false)

        verify(mockFireproofRepository, never()).fireproofWebsitesSync()
    }

    @Test
    fun whenShouldClearDuckAiDataTrueThenDuckDuckGoDomainsNotExcluded() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_duckduckgo.com_0.indexeddb.leveldb",
            "https_duck.ai_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = true)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(emptyList()),
        )
    }

    @Test
    fun whenShouldClearDuckAiDataFalseAndSettingsHasDuckDuckGoDomainsConfiguredThenDuckDuckGoDomainsExcluded() = runTest {
        val json = """{"domains":["duckduckgo.com","duck.ai"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_duckduckgo.com_0.indexeddb.leveldb",
            "https_duck.ai_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = false)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            argThat { list ->
                list.toSet() == setOf(
                    "https_duckduckgo.com_0.indexeddb.leveldb",
                    "https_duck.ai_0.indexeddb.leveldb",
                )
            },
        )
    }

    @Test
    fun whenShouldClearDuckAiDataTrueAndSettingsHasDuckDuckGoDomainsConfiguredThenDuckDuckGoDomainsNotExcluded() = runTest {
        val json = """{"domains":["duckduckgo.com","duck.ai"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_duckduckgo.com_0.indexeddb.leveldb",
            "https_duck.ai_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = true)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(emptyList()),
        )
    }

    @Test
    fun whenShouldClearDuckAiDataTrueAndSettingsHasOtherDomainsThenOtherDomainsExcludedButDuckDuckGoDomainsNotExcluded() = runTest {
        val json = """{"domains":["example.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_duckduckgo.com_0.indexeddb.leveldb",
            "https_duck.ai_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
            "https_foo.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = true)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(listOf("https_example.com_0.indexeddb.leveldb")),
        )
    }

    @Test
    fun whenShouldClearDuckAiDataFalseAndSettingsHasOtherDomainsThenAllDomainsExcluded() = runTest {
        val json = """{"domains":["example.com","duckduckgo.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_duckduckgo.com_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
            "https_foo.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = false)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            argThat { list ->
                list.toSet() == setOf(
                    "https_duckduckgo.com_0.indexeddb.leveldb",
                    "https_example.com_0.indexeddb.leveldb",
                )
            },
        )
    }

    @Test
    fun whenShouldClearDuckAiDataTrueWithSubdomainsThenDuckDuckGoSubdomainsNotExcluded() = runTest {
        val json = """{"domains":["example.com"]}"""
        whenever(mockIndexedDBToggle.getSettings()).thenReturn(json)
        whenever(mockFireproofToggle.isEnabled()).thenReturn(false)

        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        listOf(
            "https_chat.duckduckgo.com_0.indexeddb.leveldb",
            "https_www.duck.ai_0.indexeddb.leveldb",
            "https_example.com_0.indexeddb.leveldb",
        ).forEach { File(rootFolder, it).mkdirs() }

        testee.clearIndexedDB(shouldClearDuckAiData = true)

        verify(mockFileDeleter).deleteContents(
            eq(rootFolder),
            eq(listOf("https_example.com_0.indexeddb.leveldb")),
        )
    }

    @Test
    fun whenClearOnlyDuckAiDataCalledThenOnlyDuckDuckGoDomainsDeleted() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val duckDuckGoFolder = File(rootFolder, "https_duckduckgo.com_0.indexeddb.leveldb")
        val duckAiFolder = File(rootFolder, "https_duck.ai_0.indexeddb.leveldb")
        val exampleFolder = File(rootFolder, "https_example.com_0.indexeddb.leveldb")

        listOf(duckDuckGoFolder, duckAiFolder, exampleFolder).forEach { it.mkdirs() }

        testee.clearOnlyDuckAiData()

        verify(mockFileDeleter).deleteContents(eq(duckDuckGoFolder), eq(emptyList()))
        verify(mockFileDeleter).deleteContents(eq(duckAiFolder), eq(emptyList()))
        verify(mockFileDeleter, never()).deleteContents(eq(exampleFolder), any())
    }

    @Test
    fun whenClearOnlyDuckAiDataCalledWithSubdomainsThenDuckDuckGoSubdomainsDeleted() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val chatDuckDuckGoFolder = File(rootFolder, "https_chat.duckduckgo.com_0.indexeddb.leveldb")
        val wwwDuckAiFolder = File(rootFolder, "https_www.duck.ai_0.indexeddb.leveldb")
        val exampleFolder = File(rootFolder, "https_example.com_0.indexeddb.leveldb")

        listOf(chatDuckDuckGoFolder, wwwDuckAiFolder, exampleFolder).forEach { it.mkdirs() }

        testee.clearOnlyDuckAiData()

        verify(mockFileDeleter).deleteContents(eq(chatDuckDuckGoFolder), eq(emptyList()))
        verify(mockFileDeleter).deleteContents(eq(wwwDuckAiFolder), eq(emptyList()))
        verify(mockFileDeleter, never()).deleteContents(eq(exampleFolder), any())
    }

    @Test
    fun whenClearOnlyDuckAiDataCalledWithNoDuckDuckGoDomainsThemNothingDeleted() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val exampleFolder = File(rootFolder, "https_example.com_0.indexeddb.leveldb")
        val fooFolder = File(rootFolder, "https_foo.com_0.indexeddb.leveldb")

        listOf(exampleFolder, fooFolder).forEach { it.mkdirs() }

        testee.clearOnlyDuckAiData()

        verify(mockFileDeleter, never()).deleteContents(any(), any())
    }

    @Test
    fun whenClearOnlyDuckAiDataCalledWithOnlyDuckDuckGoDomainsThenAllDeleted() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val duckDuckGoFolder = File(rootFolder, "https_duckduckgo.com_0.indexeddb.leveldb")
        val duckAiFolder = File(rootFolder, "https_duck.ai_0.indexeddb.leveldb")

        listOf(duckDuckGoFolder, duckAiFolder).forEach { it.mkdirs() }

        testee.clearOnlyDuckAiData()

        verify(mockFileDeleter).deleteContents(eq(duckDuckGoFolder), eq(emptyList()))
        verify(mockFileDeleter).deleteContents(eq(duckAiFolder), eq(emptyList()))
    }

    @Test
    fun whenClearOnlyDuckAiDataCalledThenOtherDomainsPreserved() = runTest {
        val rootFolder = File(context.applicationInfo.dataDir, "app_webview/Default/IndexedDB")
        val duckDuckGoFolder = File(rootFolder, "https_duckduckgo.com_0.indexeddb.leveldb")
        val exampleFolder = File(rootFolder, "https_example.com_0.indexeddb.leveldb")
        val fooFolder = File(rootFolder, "https_foo.com_0.indexeddb.leveldb")
        val barFolder = File(rootFolder, "https_bar.com_0.indexeddb.leveldb")

        listOf(duckDuckGoFolder, exampleFolder, fooFolder, barFolder).forEach { it.mkdirs() }

        testee.clearOnlyDuckAiData()

        verify(mockFileDeleter).deleteContents(eq(duckDuckGoFolder), eq(emptyList()))
        verify(mockFileDeleter, never()).deleteContents(eq(exampleFolder), any())
        verify(mockFileDeleter, never()).deleteContents(eq(fooFolder), any())
        verify(mockFileDeleter, never()).deleteContents(eq(barFolder), any())
    }
}
