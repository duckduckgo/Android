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

import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ResourceSurrogatesTest {

    private lateinit var testee: ResourceSurrogates

    @Before
    fun setup() {
        testee = ResourceSurrogatesImpl()
    }

    @Test
    fun whenInitialisedThenHasNoSurrogatesLoaded() {
        assertEquals(0, testee.getAll().size)
    }

    @Test
    fun whenOneSurrogateLoadedThenOneReturnedFromFullList() {
        val surrogate = SurrogateResponse()
        testee.loadSurrogates(listOf(surrogate))
        assertEquals(1, testee.getAll().size)
    }

    @Test
    fun whenMultipleSurrogatesLoadedThenAllReturnedFromFullList() {
        val surrogate = SurrogateResponse()
        testee.loadSurrogates(listOf(surrogate, surrogate, surrogate))
        assertEquals(3, testee.getAll().size)
    }

    @Test
    fun whenSearchingForExactMatchingExistingSurrogateThenCanFindByName() {
        val surrogate = SurrogateResponse(name = "foo")
        testee.loadSurrogates(listOf(surrogate))
        val retrieved = testee.get(Uri.parse("foo"))
        assertTrue(retrieved.responseAvailable)
    }


    @Test
    fun whenSearchingForSubstringMatchingExistingSurrogateThenCanFindByName() {
        val surrogate = SurrogateResponse(name = "foo.com")
        testee.loadSurrogates(listOf(surrogate))
        val retrieved = testee.get(Uri.parse("foo.com/a/b/c"))
        assertTrue(retrieved.responseAvailable)
    }

    @Test
    fun whenSearchingByNonExistentNameThenResponseUnavailableSurrogateResultReturned() {
        val surrogate = SurrogateResponse(name = "foo")
        testee.loadSurrogates(listOf(surrogate))
        val retrieved = testee.get(Uri.parse("bar"))
        assertFalse(retrieved.responseAvailable)
    }
}