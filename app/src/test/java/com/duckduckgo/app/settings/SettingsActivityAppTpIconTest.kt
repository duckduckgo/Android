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

package com.duckduckgo.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import com.duckduckgo.mobile.android.R as CommonR

class SettingsActivityAppTpIconTest {

    @Test
    fun whenPictogramsAreEnabledThenAppTpUsesHomescreenLockIcon() {
        assertEquals(
            CommonR.drawable.ic_homescreen_lock_color_24,
            resolveAppTpSettingsIcon(isPictogramsEnabled = true),
        )
    }

    @Test
    fun whenPictogramsAreDisabledThenAppTpUsesSharedLockIcon() {
        assertEquals(
            CommonR.drawable.ic_lock_color_24,
            resolveAppTpSettingsIcon(isPictogramsEnabled = false),
        )
    }
}
