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

package com.duckduckgo.mobile.android.vpn.model

import org.junit.Assert.*

import org.junit.Before
import org.junit.Test
import java.text.NumberFormat
import java.util.*

class DataSizeFormatterTest {

    private lateinit var testee: DataSizeFormatter

    @Before
    fun setup() {
        testee = DataSizeFormatter(NumberFormat.getNumberInstance(Locale.US).also { it.maximumFractionDigits = 1 })
    }

    @Test
    fun whenNoDataThen0BytesReturned() {
        assertEquals("0 bytes", testee.format(0))
    }

    @Test
    fun whenLessThat1KbThenBytesReturned() {
        assertEquals("100 bytes", testee.format(100))
    }

    @Test
    fun whenExactlyOn1KbThenKbReturned() {
        assertEquals("1 KB", testee.format(1000))
    }

    @Test
    fun whenNotAWholeNumberOfKilobytesThenKbReturned() {
        assertEquals("1.5 KB", testee.format(1501))
    }

    @Test
    fun whenExactly1MegabyteThenMbReturned() {
        assertEquals("1 MB", testee.format(1_000_000))
    }

    @Test
    fun whenExactly1GigabyteThenGbReturned() {
        assertEquals("1 GB", testee.format(1_000_000_000))
    }
}
