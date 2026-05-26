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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.navigation.api.GlobalActivityStarter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuckPlayerSettingsEntryTest {

    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockStatusChecker: AdBlockingStatusChecker = mock()
    private val mockPixel: Pixel = mock()
    private val mockActivityStarter: GlobalActivityStarter = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val entry = DuckPlayerSettingsEntry(
        statusChecker = mockStatusChecker,
        duckPlayer = mockDuckPlayer,
        pixel = mockPixel,
        globalActivityStarter = mockActivityStarter,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenAdBlockingShownInSettingsThenDuckPlayerNotShown() {
        whenever(mockStatusChecker.isShownInSettings()) doReturn true
        whenever(mockDuckPlayer.getDuckPlayerState()) doReturn ENABLED

        assertFalse(entry.isShownInSettings())
    }

    @Test
    fun whenAdBlockingNotShownInSettingsAndDuckPlayerEnabledThenDuckPlayerShown() {
        whenever(mockStatusChecker.isShownInSettings()) doReturn false
        whenever(mockDuckPlayer.getDuckPlayerState()) doReturn ENABLED

        assertTrue(entry.isShownInSettings())
    }

    @Test
    fun whenAdBlockingNotShownInSettingsAndDuckPlayerDisabledWithHelpKinkThenDuckPlayerShown() {
        whenever(mockStatusChecker.isShownInSettings()) doReturn false
        whenever(mockDuckPlayer.getDuckPlayerState()) doReturn DISABLED_WIH_HELP_LINK

        assertTrue(entry.isShownInSettings())
    }

    @Test
    fun whenAdBlockingNotShownInSettingsAndDuckPlayerDisabledThenDuckPlayerNotShown() {
        whenever(mockStatusChecker.isShownInSettings()) doReturn false
        whenever(mockDuckPlayer.getDuckPlayerState()) doReturn DISABLED

        assertFalse(entry.isShownInSettings())
    }
}
