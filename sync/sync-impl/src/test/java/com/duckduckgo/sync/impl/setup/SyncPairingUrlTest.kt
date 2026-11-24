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

package com.duckduckgo.sync.impl.setup

import com.duckduckgo.sync.impl.setup.SyncPairingUrl.DuckAi
import com.duckduckgo.sync.impl.setup.SyncPairingUrl.DuckCom
import com.google.testing.junit.testparameterinjector.TestParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector

@RunWith(RobolectricTestParameterInjector::class)
class SyncPairingUrlTest {

    @Test
    fun `matches duck_com pairing url`(
        @TestParameter(
            "https://duck.com/pair",
            "https://duck.com/pair/",
            "http://duck.com/pair",
            "https://www.duck.com/pair",
            "https://DUCK.COM/Pair",
            "https://duck.com/pair#code2=ABC",
            "https://duck.com/pair?code2=ABC",
        ) url: String,
    ) {
        assertEquals(DuckCom, SyncPairingUrl.fromUrl(url))
    }

    @Test
    fun `matches duck_ai pairing url`(
        @TestParameter(
            "https://duck.ai/pair",
            "https://www.duck.ai/pair/",
            "https://duck.ai/pair#entry=landing&code2=ABC",
        ) url: String,
    ) {
        assertEquals(DuckAi, SyncPairingUrl.fromUrl(url))
    }

    @Test
    fun `rejects other urls`(
        @TestParameter(
            "https://duck.com/",
            "https://duck.com/pairing",
            "https://duck.com/pair/extra",
            "https://duck.ai/chat",
            "https://duckduckgo.com/pair",
            "https://duckduckgo.com/sync/pairing/#&code=ABC",
            "https://evil.com/pair",
            "https://duck.com.evil.com/pair",
            "ftp://duck.com/pair",
            "duck.com/pair",
            "not a url",
        ) url: String,
    ) {
        assertNull(SyncPairingUrl.fromUrl(url))
    }
}
