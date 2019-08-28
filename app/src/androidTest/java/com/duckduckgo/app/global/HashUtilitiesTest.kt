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

package com.duckduckgo.app.global

import org.junit.Assert.*
import org.junit.Test

class HashUtilitiesTest {

    @Test
    fun whenSha1HashCalledOnStringThenResultIsCorrect() {
        val result = helloWorldText.sha1
        assertEquals(helloWorldSha1, result)
    }

    @Test
    fun whenSha256HashCalledOnBytesThenResultIsCorrect() {
        val result = helloWorldText.toByteArray().sha256
        assertEquals(helloWorldSha256, result)
    }

    @Test
    fun whenCorrectSha256HashUsedThenVerifyIsTrue() {
        assertTrue(helloWorldText.toByteArray().verifySha256(helloWorldSha256))
    }

    @Test
    fun whenIncorrectByteSha256HashUsedThenVerifyIsFalse() {
        assertFalse(helloWorldText.toByteArray().verifySha256(otherSha256))
    }

    companion object {
        const val helloWorldText = "Hello World!"
        const val helloWorldSha256 = "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069"
        const val helloWorldSha1 = "2ef7bde608ce5404e97d5f042f95f89f1c232871"
        const val otherSha256 = "f97e9da0e3b879f0a9df979ae260a5f7e1371edb127c1862d4f861981166cdc1"
    }
}