/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class ListExtensionKtTest(private val testCase: TestListTestCase) {

    @Test
    fun testFilterBlankItems() {
        with(testCase) { assertEquals(expected, inputList.filterBlankItems()) }
    }

    companion object {

        @JvmStatic
        @Parameters(name = "Test case: {index} - {0}")
        fun data(): Array<TestListTestCase> =
            arrayOf(
                TestListTestCase(listOf(""), emptyList()),
                TestListTestCase(listOf(" "), emptyList()),
                TestListTestCase(listOf("foo.bar"), listOf("foo.bar")),
                TestListTestCase(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", ""), listOf("foo.bar")),
                TestListTestCase(listOf("foo.bar", " "), listOf("foo.bar")),
                TestListTestCase(listOf("", "foo.bar"), listOf("foo.bar")),
                TestListTestCase(listOf(" ", "foo.bar"), listOf("foo.bar")),
                TestListTestCase(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", "", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", " ", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", "ddg.com", " "), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", "ddg.com", ""), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf("", "foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")),
                TestListTestCase(listOf(" ", "foo.bar", "ddg.com"), listOf("foo.bar", "ddg.com")))
    }

    data class TestListTestCase(val inputList: List<String>, val expected: List<String>)
}
