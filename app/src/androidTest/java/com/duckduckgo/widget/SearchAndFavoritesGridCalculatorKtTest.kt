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

package com.duckduckgo.widget

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

import org.junit.Assert.*
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class SearchAndFavoritesGridCalculatorKtTest {

    @RunWith(Parameterized::class)
    class SearchAndFavoritesGridColumnCalculatorKtTest(private val testCase: TestCase) {

        private val context = InstrumentationRegistry.getInstrumentation().targetContext

        private val testee = SearchAndFavoritesGridCalculator()

        @Test
        fun calculateColumnsBasedOnAvailableWidth() {
            val columns = testee.calculateColumns(context, testCase.width)

            assertEquals(testCase.expectedColumns, columns)
        }

        companion object {
            data class TestCase(val expectedColumns: Int, val width: Int)

            @JvmStatic
            @Parameterized.Parameters(name = "Test case: {index} - {0}")
            fun testData(): Array<TestCase> {
                return arrayOf(
                    TestCase(2, 100),
                    TestCase(2, 144),
                    TestCase(3, 212),
                    TestCase(3, 279),
                    TestCase(4, 280),
                    TestCase(5, 348),
                    TestCase(6, 416),
                    TestCase(7, 484),
                    TestCase(8, 552)
                )
            }
        }
    }

    @RunWith(Parameterized::class)
    class SearchAndFavoritesGridRowsCalculatorKtTest(private val testCase: TestCase) {

        private val context = InstrumentationRegistry.getInstrumentation().targetContext

        private val testee = SearchAndFavoritesGridCalculator()

        @Test
        fun calculateRowsBasedOnAvailableHeight() {
            val rows = testee.calculateRows(context, testCase.width)

            assertEquals(testCase.expectedRows, rows)
        }

        companion object {
            data class TestCase(val expectedRows: Int, val width: Int)

            @JvmStatic
            @Parameterized.Parameters(name = "Test case: {index} - {0}")
            fun testData(): Array<TestCase> {
                return arrayOf(
                    TestCase(1, 100),
                    TestCase(1, 172),
                    TestCase(2, 270),
                    TestCase(3, 368),
                    TestCase(3, 465),
                    TestCase(4, 466),
                    TestCase(4, 564),
                    TestCase(4, 662),
                    TestCase(4, 760),
                    TestCase(4, 858)
                )
            }
        }
    }
}
