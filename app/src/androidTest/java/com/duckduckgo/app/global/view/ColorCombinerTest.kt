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

package com.duckduckgo.app.global.view

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorCombinerTest {

    private val testee = ColorCombiner()

    @Test
    fun whenColorsCombineWithZeroRatioThenCombinedColorIsFirstColor() {
        val from = Color.argb(200, 200, 200, 200)
        val to = Color.argb(100, 100, 100, 100)
        assertEquals(from, testee.combine(from, to, 0.toFloat()))
    }

    @Test
    fun whenColorsCombineWithFullRatioThenCombinedColorIsSecondColor() {
        val from = Color.argb(200, 200, 200, 200)
        val to = Color.argb(100, 100, 100, 100)
        assertEquals(to, testee.combine(from, to, 1.toFloat()))
    }

    @Test
    fun whenColorsCombinedWithHalfRatioThenCombinedColorIsMixed() {
        val from = Color.argb(200, 200, 200, 200)
        val to = Color.argb(100, 100, 100, 100)
        val expected = Color.argb(150, 150, 150, 150)
        val actual = testee.combine(from, to, 0.5.toFloat())
        assertEquals(expected, actual)
    }
}