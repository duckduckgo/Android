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

package com.duckduckgo.app.trackerdetection

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test

class RealUrlToTypeMapperTest {

    private lateinit var testee: UrlToTypeMapper

    @Before
    fun setup() {
        testee = RealUrlToTypeMapper()
    }

    @Test
    fun whenUrlHasNoExtensionThenReturnNull() {
        assertNull(testee.map("example.com"))
    }

    @Test
    fun whenUrlHasImageExtensionThenReturnImage() {
        assertEquals("image", testee.map("example.com/test.jpg"))
        assertEquals("image", testee.map("example.com/test.png"))
        assertEquals("image", testee.map("example.com/test.gif"))
        assertEquals("image", testee.map("example.com/test.svg"))
        assertEquals("image", testee.map("example.com/test.bmp"))
        assertEquals("image", testee.map("example.com/test.tif"))
    }

    @Test
    fun whenUrlHasScriptExtensionThenReturnScript() {
        assertEquals("script", testee.map("example.com/test.js"))
    }

    @Test
    fun whenUrlHasStylesheetExtensionThenReturnStylesheet() {
        assertEquals("stylesheet", testee.map("example.com/test.css"))
    }
}
