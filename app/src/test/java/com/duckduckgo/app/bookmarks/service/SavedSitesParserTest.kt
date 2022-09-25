/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.service

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SavedSitesParserTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var parser: RealSavedSitesParser

    private var mockBookmarksRepository: BookmarksRepository = mock()

    @Before
    fun before() {
        parser = RealSavedSitesParser()
        runBlocking {
            whenever(mockBookmarksRepository.insert(any<BookmarkFolder>())).thenReturn(0L)
        }
    }

    @Test
    fun whenSomeBookmarksExistThenHtmlIsGenerated() = runTest {
        val bookmark = SavedSite.Bookmark(id = 1, title = "example", url = "www.example.com", 0)
        val favorite = SavedSite.Favorite(id = 1, title = "example", url = "www.example.com", 0)

        val node = TreeNode(FolderTreeItem(0, RealSavedSitesParser.BOOKMARKS_FOLDER, -1, null, 0))
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

        assertEquals(expectedHtml, result)
    }

    @Test
    fun whenNoSavedSitesExistThenNothingIsGenerated() = runTest {
        val node = TreeNode(FolderTreeItem(0, RealSavedSitesParser.BOOKMARKS_FOLDER, -1, null, 0))

        val result = parser.generateHtml(node, emptyList())
        val expectedHtml = ""

        assertEquals(expectedHtml, result)
    }

    @Test
    fun doesNotImportAnythingWhenFileIsNotProperlyFormatted() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_invalid.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        Assert.assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun canImportFromFirefox() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_firefox.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(17, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals("https://support.mozilla.org/en-US/products/firefox", firstBookmark.url)
        assertEquals("Get Help", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.mozilla.org/en-US/firefox/central/", lastBookmark.url)
        assertEquals("Getting Started", lastBookmark.title)
    }

    @Test
    fun canImportFromBrave() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_brave.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(12, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url
        )
        assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.macrumors.com/", lastBookmark.url)
        assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportFromChrome() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_chrome.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(12, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url
        )
        assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.macrumors.com/", lastBookmark.url)
        assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksFromDDGAndroid() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_ddg_android.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(13, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url
        )
        assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.apple.com/uk/", lastBookmark.url)
        assertEquals("Apple (United Kingdom)", lastBookmark.title)
        assertTrue(lastBookmark is Favorite)
    }

    @Test
    fun canImportBookmarksFromDDGMacOS() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_ddg_macos.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(13, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://www.theguardian.com/international",
            firstBookmark.url
        )
        assertEquals("News, sport and opinion from the Guardian's global edition | The Guardian", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.apple.com/uk/", lastBookmark.url)
        assertEquals("Apple (United Kingdom)", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksFromSafari() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_safari.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document, mockBookmarksRepository)

        assertEquals(14, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://www.apple.com/uk",
            firstBookmark.url
        )
        assertEquals("Apple", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://www.macrumors.com/", lastBookmark.url)
        assertEquals("MacRumors: Apple News and Rumors", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksAndFavoritesFromDDG() = runTest {
        val inputStream = FileUtilities.loadResource(javaClass.classLoader!!, "bookmarks/bookmarks_favorites_ddg.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val savedSites = parser.parseHtml(document, mockBookmarksRepository)

        val favorites = savedSites.filterIsInstance<SavedSite.Favorite>()
        val bookmarks = savedSites.filterIsInstance<SavedSite.Bookmark>()

        assertEquals(12, savedSites.size)
        assertEquals(3, favorites.size)
        assertEquals(9, bookmarks.size)
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
                        savedSites.add(SavedSite.Favorite(0, title = title, url = link, favorites))
                        favorites++
                    } else {
                        savedSites.add(SavedSite.Bookmark(0, title = title, url = link, parentId = 0))
                    }
                }
            }
        }

        val favoritesLists = savedSites.filterIsInstance<SavedSite.Favorite>()
        val bookmarks = savedSites.filterIsInstance<SavedSite.Bookmark>()

        assertEquals(12, savedSites.size)
        assertEquals(3, favoritesLists.size)
        assertEquals(9, bookmarks.size)
    }
}
