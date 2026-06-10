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

package com.duckduckgo.app.browser.downloader

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BlobConverterJavascriptInterfaceTest {

    lateinit var testee: BlobConverterJavascriptInterface
    var result: String = ""

    @Before
    fun setup() {
        testee = BlobConverterJavascriptInterface { first, second -> result = first + second }
    }

    @Test
    fun whenConvertToBlobDataUriThenLambdaCalled() {
        testee.convertBlobToDataUri("first", "second")

        assertEquals("firstsecond", result)
    }
}
