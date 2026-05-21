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

import android.content.Context
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AdBlockingSettingsEntryTest {

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val adBlockingTileFactory: AdBlockingTileFactory = mock()
    private val duckPlayerTileFactory: DuckPlayerTileFactory = mock()
    private val context: Context = mock()

    private val entry = AdBlockingSettingsEntry(
        statusChecker = statusChecker,
        adBlockingTileFactory = adBlockingTileFactory,
        duckPlayerTileFactory = duckPlayerTileFactory,
    )

    @Test
    fun whenAdBlockingShownInSettingsThenAdBlockingTileIsReturned() {
        whenever(statusChecker.isShownInSettings()) doReturn true

        entry.getView(context)

        verify(adBlockingTileFactory).getView(context)
        verify(duckPlayerTileFactory, never()).getView(any())
    }

    @Test
    fun whenAdBlockingNotShownInSettingsThenDuckPlayerTileIsReturned() {
        whenever(statusChecker.isShownInSettings()) doReturn false

        entry.getView(context)

        verify(duckPlayerTileFactory).getView(context)
        verify(adBlockingTileFactory, never()).getView(any())
    }
}
