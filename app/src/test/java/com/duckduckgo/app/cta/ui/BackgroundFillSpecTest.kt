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

package com.duckduckgo.app.cta.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundFillSpecTest {

    @Test
    fun whenPhoneThenUsesFillHeight() {
        assertEquals(280f, BackgroundFillSpec(fillHeightDp = 280f, tabletFillHeightDp = 420f).heightDpFor(isTablet = false), 0f)
    }

    @Test
    fun whenTabletThenUsesTabletFillHeight() {
        assertEquals(420f, BackgroundFillSpec(fillHeightDp = 280f, tabletFillHeightDp = 420f).heightDpFor(isTablet = true), 0f)
    }

    @Test
    fun whenTabletHeightOmittedThenTabletDefaultsToPhone() {
        assertEquals(280f, BackgroundFillSpec(fillHeightDp = 280f).heightDpFor(isTablet = true), 0f)
    }
}
