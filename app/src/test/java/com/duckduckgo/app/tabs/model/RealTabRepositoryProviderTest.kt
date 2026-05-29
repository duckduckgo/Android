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

package com.duckduckgo.app.tabs.model

import com.duckduckgo.browsermode.api.BrowserMode.FIRE
import com.duckduckgo.browsermode.api.BrowserMode.REGULAR
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class RealTabRepositoryProviderTest {

    private val regularRepo: TabRepository = mock()
    private val fireRepo: TabRepository = mock()
    private val provider = RealTabRepositoryProvider(regularRepo, fireRepo)

    @Test
    fun whenModeIsRegularThenReturnsRegularRepository() {
        assertSame(regularRepo, provider.forMode(REGULAR))
    }

    @Test
    fun whenModeIsFireThenReturnsFireRepository() {
        assertSame(fireRepo, provider.forMode(FIRE))
    }
}
