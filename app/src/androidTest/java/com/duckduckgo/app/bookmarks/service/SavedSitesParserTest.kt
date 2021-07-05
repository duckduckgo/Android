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
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.model.SavedSite
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedSitesParserTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var parser: RealSavedSitesParser

    @Before
    fun before() {
        parser = RealSavedSitesParser()
    }

    @Test
    fun whenSomeBookmarksExistThenHtmlIsGenerated() = runBlocking {
        val bookmark = BookmarkEntity(id = 1, title = "example", url = "www.example.com")
        val favorite = SavedSite.Favorite(id = 1, title = "example", url = "www.example.com", 0)

        val result = parser.generateHtml(listOf(bookmark), listOf(favorite))
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
    fun whenNoSavedSitesExistThenNothingIsGenerated() = runBlocking {
        val result = parser.generateHtml(emptyList(), emptyList())
        val expectedHtml = ""

        assertEquals(expectedHtml, result)
    }

    @Test
    fun doesNotImportAnythingWhenFileIsNotProperlyFormatted() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_invalid.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document)

        Assert.assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun canImportFromFirefox() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_firefox.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document)

        assertEquals(7, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals("https://support.mozilla.org/en-US/products/firefox", firstBookmark.url)
        assertEquals("Get Help", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://duckduckgo.com/", lastBookmark.url)
        assertEquals("DuckDuckGo — Privacy, simplified.", lastBookmark.title)
    }

    @Test
    fun canImportFromChrome() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_chrome.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document)

        assertEquals(4, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals(
            "https://jupyter.duckduckgo.com/notebooks/Mobile/Android%20Monday%20Health%20Check%20(DO%20NOT%20CHANGE).ipynb",
            firstBookmark.url
        )
        assertEquals("Android Monday Health Check", firstBookmark.title)

        val lastBookmark = bookmarks.last()
        assertEquals("https://app.asana.com/0/414730916066338/598549668511654", lastBookmark.url)
        assertEquals("Android App Development - Android Runtime Permissions Explanations - Asana", lastBookmark.title)
    }

    @Test
    fun canImportBookmarksFromDDG() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_ddg.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val bookmarks = parser.parseHtml(document)
        assertEquals(8, bookmarks.size)

        val firstBookmark = bookmarks.first()
        assertEquals("https://as.com/", firstBookmark.url)
        assertEquals("AS.com", firstBookmark.title)
    }

    @Test
    fun canImportBookmarksAndFavoritesFromDDG() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_favorites_ddg.html")
        val document = Jsoup.parse(inputStream, Charsets.UTF_8.name(), "duckduckgo.com")

        val savedSites = parser.parseHtml(document)

        val favorites = savedSites.filterIsInstance<SavedSite.Favorite>()
        val bookmarks = savedSites.filterIsInstance<SavedSite.Bookmark>()

        assertEquals(12, savedSites.size)
        assertEquals(3, favorites.size)
        assertEquals(9, bookmarks.size)
    }

    @Test
    fun parsesBookmarksAndFavorites() {
        val inputStream = FileUtilities.loadResource("bookmarks/bookmarks_favorites_ddg.html")
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
                        savedSites.add(SavedSite.Bookmark(0, title = title, url = link))
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

    data class SavedSites(val favoriteFolder: FavoriteFolder, val bookmarkFolders: List<BookmarkFolder>)
    data class BookmarkFolder(val name: String, val bookmarks: List<SavedSite.Bookmark>)
    data class FavoriteFolder(val name: String = "Favorites", val favorites: List<SavedSite.Favorite>)

}
