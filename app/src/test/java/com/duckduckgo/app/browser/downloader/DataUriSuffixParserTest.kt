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

package com.duckduckgo.app.browser.downloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataUriSuffixParserTest {

    private lateinit var testee: DataUriSuffixParser

    @Before
    fun before() {
        testee = DataUriSuffixParser()
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsPngThenSuffixIsPng() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,iVBORw0KGgo",
            data = "iVBORw0KGgo"
        )

        assertEquals("png", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsJfifThenSuffixIsJpg() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,/9j/4AAQSkZJRgABAQ",
            data = "/9j/4AAQSkZJRgABAQ"
        )

        assertEquals("jpg", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsSvgThenSuffixIsSvg() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,PHN2ZyB2ZXJzaW9uPSIxLjIiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy",
            data = "PHN2ZyB2ZXJzaW9uPSIxLjIiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy"
        )

        assertEquals("svg", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsGifThenSuffixIsGif() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,R0lGODlhAAXQAocAAP",
            data = "R0lGODlhAAXQAocAAP"
        )

        assertEquals("gif", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsPdfThenSuffixIsPdf() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,JVBERi0xLjEKMSAwIG9iag",
            data = "JVBERi0xLjEKMSAwIG9iag"
        )

        assertEquals("pdf", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsWebpThenSuffixIsWebp() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,UklGRs4IAABXRUJQVlA4WAo",
            data = "UklGRs4IAABXRUJQVlA4WAo"
        )

        assertEquals("webp", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsBpThenSuffixIsBmp() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,Qk1AwgEA",
            data = "Qk1AwgEA"
        )

        assertEquals("bmp", suffix)
    }

    @Test
    fun whenMimeTypeIsTextPlainAndDataIsBase64AndIsUnknownThenSuffixIsEmpty() {
        val suffix = testee.parseSuffix(
            mimeType = "text/plain",
            url = "data:text/plain;base64,RUJQVlA4WAo",
            data = "RUJQVlA4WAo"
        )

        assertEquals("", suffix)
    }

    @Test
    fun whenMimeTypeIsImageJpegThenSuffixIsJpg() {
        val suffix = testee.parseSuffix(
            mimeType = "image/jpeg",
            url = "data:text/plain;base64,RUJQVlA4WAo",
            data = "RUJQVlA4WAo"
        )

        assertEquals("jpg", suffix)
    }
}
