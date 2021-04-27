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
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BookmarkParserTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var parser: BookmarksParser

    @Before
    fun before() {
        parser = DuckDuckGoBookmarksParser()
    }

    @Test
    fun whenSomeBookmarksExistThenHtmlIsGenerated() = runBlocking {
        val bookmark = BookmarkEntity(id = 1, title = "example", url = "www.example.com")

        val result = parser.generateHtml(listOf(bookmark))
        val expectedHtml = "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n" +
            "<!--This is an automatically generated file.\n" +
            "It will be read and overwritten.\n" +
            "Do Not Edit! -->\n" +
            "<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n" +
            "<Title>Bookmarks</Title>\n" +
            "<H1>Bookmarks</H1>\n" +
            "<DL><p>\n" +
            "    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\" PERSONAL_TOOLBAR_FOLDER=\"true\">DuckDuckGo</H3>\n" +
            "    <DL><p>\n" +
            "        <DT><A HREF=\"www.example.com\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">example</A>\n" +
            "    </DL><p>\n" +
            "</DL><p>\n"

        assertEquals(expectedHtml, result)
    }

    @Test
    fun whenNoBookmarksExistThenNothingIsGenerated() = runBlocking {
        val result = parser.generateHtml(emptyList())
        val expectedHtml = ""

        assertEquals(expectedHtml, result)
    }

}
