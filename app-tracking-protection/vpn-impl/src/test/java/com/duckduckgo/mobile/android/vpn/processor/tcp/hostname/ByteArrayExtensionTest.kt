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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import org.junit.Assert
import org.junit.Test
import java.nio.charset.StandardCharsets

class ByteArrayExtensionTest {
    private val testee = "test_string".toByteArray(StandardCharsets.US_ASCII)
    private val testeeSize = testee.size

    @Test
    fun whenEmptySequenceReturnNegative() {
        Assert.assertEquals(-1, testee.indexOf("".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun whenOffsetTooLargeReturnNegative() {
        Assert.assertEquals(-1, testee.indexOf("test".toByteArray(StandardCharsets.US_ASCII), testeeSize))
    }

    @Test
    fun whenSequenceFoundReturnIdx() {
        Assert.assertEquals(4, testee.indexOf("test".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun whenSequenceNotFoundReturnNegative() {
        Assert.assertEquals(-1, testee.indexOf("tests".toByteArray(StandardCharsets.US_ASCII)))
    }
}
