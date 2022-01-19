/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.surrogates

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ResourceSurrogateLoaderTest {

    private lateinit var testee: ResourceSurrogateLoader
    private lateinit var dataStore: ResourceSurrogateDataStore
    private lateinit var resourceSurrogates: ResourceSurrogates

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        resourceSurrogates = ResourceSurrogatesImpl()
        dataStore = ResourceSurrogateDataStore(InstrumentationRegistry.getInstrumentation().targetContext)
        testee = ResourceSurrogateLoader(TestScope(), resourceSurrogates, dataStore)
    }

    @Test
    fun whenLoading6SurrogatesThen6SurrogatesFound() {
        val surrogates = initialiseFile("surrogates_6")
        Assert.assertEquals(6, surrogates.size)
    }

    @Test
    fun whenLoading1SurrogateThen1SurrogateFound() {
        val surrogates = initialiseFile("surrogates_1")
        Assert.assertEquals(1, surrogates.size)
    }

    @Test
    fun whenLoadingWithNoEmptyLineAtEndOfFileThenLastSurrogateStillFound() {
        val surrogates = initialiseFile("surrogates_no_empty_line_at_end_of_file")
        assertEquals("googletagmanager.com/gtm.js", surrogates[5].name)
    }

    @Test
    fun whenLoadingWithEmptyLineAtEndOfFileThenLastSurrogateStillFound() {
        val surrogates = initialiseFile("surrogates_with_empty_line_at_end_of_file")
        assertEquals("googletagmanager.com/gtm.js", surrogates[5].name)
    }

    @Test
    fun whenLoadingMultipleSurrogatesThenOrderIsPreserved() {
        val surrogates = initialiseFile("surrogates_6")
        assertEquals("google-analytics.com/ga.js", surrogates[0].name)
        assertEquals("google-analytics.com/analytics.js", surrogates[1].name)
        assertEquals("google-analytics.com/inpage_linkid.js", surrogates[2].name)
        assertEquals("google-analytics.com/cx/api.js", surrogates[3].name)
        assertEquals("googletagservices.com/gpt.js", surrogates[4].name)
        assertEquals("googletagmanager.com/gtm.js", surrogates[5].name)
    }

    @Test
    fun whenLoadingSurrogateThenMimeTypeIsPreserved() {
        val surrogates = initialiseFile("surrogates_with_different_mime_types")
        assertEquals("text/plain", surrogates[0].mimeType)
        assertEquals("application/javascript", surrogates[1].mimeType)
        assertEquals("application/json", surrogates[2].mimeType)
    }

    @Test
    fun whenLoadingSurrogateThenFunctionLengthIsPreserved() {
        val surrogates = initialiseFile("surrogates_6")
        val actualNumberOfLines = surrogates[0].jsFunction.reader().readLines().size
        assertEquals(3, actualNumberOfLines)
    }

    @Test
    fun whenLoadingSurrogateThenFunctionLengthIsPreservedJavascriptCommentsArePreserved() {
        val surrogates = initialiseFile("surrogates_6")
        val actualNumberOfLines = surrogates[1].jsFunction.reader().readLines().size
        assertEquals(5, actualNumberOfLines)
    }

    @Test
    fun whenSurrogateFileIsMissingMimeTypeEmptyListReturned() {
        val surrogates = initialiseFile("surrogates_invalid_format_missing_mimetypes")
        assertEquals(0, surrogates.size)
    }

    @Test
    fun whenSurrogateFileIsHasSpaceInFinalFunctionBlock() {
        val surrogates = initialiseFile("surrogates_valid_but_unexpected_extra_space_in_function_close")
        assertEquals(6, surrogates.size)
    }

    @Test
    fun whenLoadingSurrogatesThenCorrectScriptIdStored() {
        val surrogates = initialiseFile("surrogates_6")
        assertEquals("ga.js", surrogates[0].scriptId)
        assertEquals("analytics.js", surrogates[1].scriptId)
        assertEquals("inpage_linkid.js", surrogates[2].scriptId)
        assertEquals("api.js", surrogates[3].scriptId)
        assertEquals("gpt.js", surrogates[4].scriptId)
        assertEquals("gtm.js", surrogates[5].scriptId)
    }

    private fun initialiseFile(filename: String): List<SurrogateResponse> {
        return testee.convertBytes(readFile(filename))
    }

    private fun readFile(filename: String): ByteArray {
        return javaClass.classLoader!!.getResource("binary/surrogates/$filename").readBytes()
    }
}
