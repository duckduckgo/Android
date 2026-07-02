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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.browsermode.api.BrowserMode
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class RealDuckAiBridgeStorageProviderTest {

    private val regular: DuckAiBridgeStorage = mock()
    private val fire: DuckAiBridgeStorage = mock()
    private val provider = RealDuckAiBridgeStorageProvider(regular, fire)

    @Test
    fun `forMode REGULAR returns the regular storage`() {
        assertSame(regular, provider.forMode(BrowserMode.REGULAR))
    }

    @Test
    fun `forMode FIRE returns the fire storage`() {
        assertSame(fire, provider.forMode(BrowserMode.FIRE))
    }
}
