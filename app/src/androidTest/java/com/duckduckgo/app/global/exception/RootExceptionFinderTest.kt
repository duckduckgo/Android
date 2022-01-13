/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.exception

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RootExceptionFinderTest {

    private lateinit var testee: RootExceptionFinder
    private val actualRoot = IllegalArgumentException("0")

    @Before
    fun setup() {
        testee = RootExceptionFinder()
    }

    @Test
    fun whenExceptionIsNullThenNullIsReturned() {
        val calculatedRoot = testee.findRootException(null)
        assertNull(calculatedRoot)
    }

    @Test
    fun whenExceptionHasNoAdditionalCausesItIsIdentifiedAsRoot() {
        val calculatedRoot = testee.findRootException(actualRoot)
        assertRootFound(calculatedRoot)
    }

    @Test
    fun whenSingleCauseThenCorrectRootIsFound() {
        val nestedException = buildNestedException(actualRoot, 1)
        val calculatedRoot = testee.findRootException(nestedException)
        assertRootFound(calculatedRoot)
    }

    @Test
    fun whenManyCausesThenCorrectRootIsFound() {
        val nestedException = buildNestedException(actualRoot, 15)
        val calculatedRoot = testee.findRootException(nestedException)
        assertRootFound(calculatedRoot)
    }

    @Test
    fun whenNestedCausesAreTooDeepThenReturnsExceptionFoundAtThreshold() {
        val nestedException = buildNestedException(actualRoot, 100)
        val calculatedRoot = testee.findRootException(nestedException)
        assertRootNotNull(calculatedRoot)
        assertEquals("80", calculatedRoot?.message)
    }

    private fun assertRootFound(calculatedRoot: Throwable?) {
        assertRootNotNull(calculatedRoot)
        assertEquals("Wrong cause identified", calculatedRoot?.message, actualRoot.message)
    }

    private fun assertRootNotNull(calculatedRoot: Throwable?) {
        assertNotNull("Root was null; not found correctly", calculatedRoot)
    }

    private fun buildNestedException(
        root: Throwable,
        depthRequired: Int
    ): Throwable {
        var nested = root
        for (i in 0 until depthRequired) {
            nested = IllegalArgumentException((i + 1).toString(), nested)
        }
        return nested
    }
}
