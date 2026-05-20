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

package com.duckduckgo.adblocking.impl.ui

import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.navigation.api.GlobalActivityStarter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdBlockingSettingsEntryTest {

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val activityStarter: GlobalActivityStarter = mock()

    private val entry = AdBlockingSettingsEntry(
        statusChecker = statusChecker,
        globalActivityStarter = activityStarter,
    )

    @Test
    fun whenAdBlockingShownInSettingsThenShownInSettings() {
        whenever(statusChecker.isShownInSettings()) doReturn true
        assertTrue(entry.isShownInSettings())
    }

    @Test
    fun whenAdBlockingShownInSettingsFalseThenShownInSettingsFalse() {
        whenever(statusChecker.isShownInSettings()) doReturn false
        assertFalse(entry.isShownInSettings())
    }
}
