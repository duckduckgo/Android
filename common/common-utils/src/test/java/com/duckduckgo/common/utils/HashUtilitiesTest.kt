/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.common.utils

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HashUtilitiesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun hashFileContentsAsLowercaseHex() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", file.sha256())
    }

    @Test
    fun hashFileTheSameAsItsBytes() {
        val bytes = ByteArray(100_000) { it.toByte() }
        val file = temporaryFolder.newFile().apply { writeBytes(bytes) }

        assertEquals(bytes.sha256, file.sha256())
    }
}
