/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeDisplayModeSettingsRepository
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.models.TreeNode
import com.duckduckgo.savedsites.impl.FavoritesDelegateImpl
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.service.FolderTreeItem
import com.duckduckgo.savedsites.impl.service.RealSavedSitesParser
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SavedSitesParserTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var parser: RealSavedSitesParser

    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository

    private val nativeLib: SyncLib = mock()
    private val store: SyncStore = mock()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        val savedSitesSettingsRepository = FakeDisplayModeSettingsRepository()
        val favoritesDelegate = FavoritesDelegateImpl(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSitesSettingsRepository,
            coroutinesTestRule.testDispatcherProvider,
        )
        repository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            coroutinesTestRule.testDispatcherProvider,
        )
        parser = RealSavedSitesParser()

        whenever(store.primaryKey).thenReturn("primaryKey")

        whenever(nativeLib.encryptData(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenAnswer { invocation -> EncryptResult(result = 0L, encryptedData = invocation.getArgument(0)) }
    }

    @Test
    fun whenSomeBookmarksExistThenHtmlIsGenerated() = runTest {
        val bookmark = SavedSite.Bookmark(
            id = "bookmark1",
            title = "example",
            url = "www.example.com",
            SavedSitesNames.BOOKMARKS_ROOT,
            lastModified = "timestamp",
        )
        val favorite = SavedSite.Favorite(id = "fav1", title = "example", url = "www.example.com", lastModified = "timestamp", 0)

        val node = TreeNode(FolderTreeItem(SavedSitesNames.BOOKMARKS_ROOT, RealSavedSitesParser.BOOKMARKS_FOLDER, "", null, 0))
        node.add(TreeNode(FolderTreeItem(bookmark.id, bookmark.title, bookmark.parentId, bookmark.url, 1)))

        val result = parser.generateHtml(node, listOf(favorite))
        val expectedHtml = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n" +
            "<!--This is an automatically generated file.\n" +
            "It will be read and overwritten.\n" +
            "Do Not Edit! -->\n" +
            "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n" +
            "<Title>Bookmarks</Title>\n" +
            "<H1>Bookmarks</H1>\n" +
            "<DL><p>\n" +
            "    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\" PERSONAL_TOOLBAR_FOLDER=\"true\">DuckDuckGo Bookmarks</H3>\n" +
            "    <DL><p>\n" +
            "        <DT><A HREF=\"www.example.com\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">example</A>\n" +
            "    </DL><p>\n" +
            "    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">DuckDuckGo Favorites</H3>\n" +
            "    <DL><p>\n" +
            "        <DT><A HREF=\"www.example.com\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">example</A>\n" +
            "    </DL><p>\n" +
            "</DL><p>\n"

        Assert.assertEquals(expectedHtml, result)
    }

    @Test
    fun whenNoSavedSitesExistThenNothingIsGenerated() = runTest {
        val node = TreeNode(FolderTreeItem(SavedSitesNames.BOOKMARKS_ROOT, RealSavedSitesParser.BOOKMARKS_FOLDER, "", null, 0))

        val result = parser.generateHtml(node, emptyList())
        val expectedHtml = ""

        Assert.assertEquals(expectedHtml, result)
    }

    @Test
    fun doesNotImportAnythingWhenFileIsNotProperlyFormatted() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_invalid.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun canImportFromFirefox() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_firefox.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(17, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals("https://support.mozilla.org/en-US/products/firefox", firstBookmark.url)
        Assert.assertEquals("Get Help", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.mozilla.org/en-US/firefox/central/", lastBookmark.url)
        Assert.assertEquals("Getting Started", lastBookmark.title)
    }

    @Test
    fun canImportFromBrave() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_brave.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(12, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url,
        )
        Assert.assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.macrumors.com/", lastBookmark.url)
        Assert.assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportFromChrome() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_chrome.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(12, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url,
        )
        Assert.assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.macrumors.com/", lastBookmark.url)
        Assert.assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksFromDDGAndroid() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_ddg_android.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(13, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url,
        )
        Assert.assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.apple.com/uk/", lastBookmark.url)
        Assert.assertEquals("Apple (United Kingdom)", lastBookmark.title)
        Assert.assertTrue(lastBookmark is Favorite)
    }

    @Test
    fun canImportBookmarksFromDDGMacOS() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_ddg_macos.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(13, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url,
        )
        Assert.assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.apple.com/uk/", lastBookmark.url)
        Assert.assertEquals("Apple (United Kingdom)", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksFromSafari() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_safari.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, repository)

        Assert.assertEquals(14, bookmarks.size)

        val firstBookmark = bookmarks.first()
        Assert.assertEquals(
            "https://www.apple.com/uk",
            firstBookmark.url,
        )
        Assert.assertEquals("Apple", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        Assert.assertEquals("https://www.macrumors.com/", lastBookmark.url)
        Assert.assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksAndFavoritesFromDDG() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_favorites_ddg.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val savedSites = parser.parseHtml(document, repository)

        val favorites = savedSites.filterIsInstance<SavedSite.Favorite>()
        val bookmarks = savedSites.filterIsInstance<SavedSite.Bookmark>()

        Assert.assertEquals(12, savedSites.size)
        Assert.assertEquals(3, favorites.size)
        Assert.assertEquals(9, bookmarks.size)
    }

    @Test
    fun parsesBookmarksAndFavorites() {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_favorites_ddg.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val savedSites = mutableListOf<SavedSite>()

        var inFolder = false
        var inFavorite = false

        var favorites = 0
        val fileItems = document.select("DT")

        fileItems.forEach {
            if (it.select("H3").isNotEmpty()) {
                val folderItem = it.select("H3")
                if (inFolder) {
                    // we get here when a folder has been read and a new one starts
                    // bookmarkFolders.add(bookmarkFolder)
                }

                val folderName = folderItem.text()
                if (folderName == "DuckDuckGo Favorites") {
                    inFavorite = true
                    inFolder = false
                } else {
                    inFolder = true
                    inFavorite = false
                    // we start a new folder
                    // bookmarks.clear()
                    // bookmarkFolder = BookmarkFolder(folderName, bookmarks)
                }
            } else {
                val linkItem = it.select("a")
                if (linkItem.isNotEmpty()) {
                    val link = linkItem.attr("href")
                    val title = linkItem.text()
                    if (inFavorite) {
                        savedSites.add(SavedSite.Favorite("favorite1", title = title, url = link, "timestamp", favorites))
                        favorites++
                    } else {
                        savedSites.add(
                            SavedSite.Bookmark(
                                "bookmark1",
                                title = title,
                                url = link,
                                parentId = SavedSitesNames.BOOKMARKS_ROOT,
                                lastModified = "timestamp",
                            ),
                        )
                    }
                }
            }
        }

        val favoritesLists = savedSites.filterIsInstance<SavedSite.Favorite>()
        val bookmarks = savedSites.filterIsInstance<SavedSite.Bookmark>()

        Assert.assertEquals(12, savedSites.size)
        Assert.assertEquals(3, favoritesLists.size)
        Assert.assertEquals(9, bookmarks.size)
    }
}
