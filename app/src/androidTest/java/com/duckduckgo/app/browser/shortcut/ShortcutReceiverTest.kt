/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.shortcut

import android.content.Intent
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.useourapp.UseOurAppDetector.Companion.USE_OUR_APP_SHORTCUT_URL
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ShortcutReceiverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockUserEventsStore: UserEventsStore = mock()
    private val mockPixel: Pixel = mock()
    private val mockDismissedCtaDao: DismissedCtaDao = mock()
    private lateinit var testee: ShortcutReceiver

    @Before
    fun before() {
        testee = ShortcutReceiver(mockUserEventsStore, mockDismissedCtaDao, coroutinesTestRule.testDispatcherProvider, mockPixel)
    }

    @Test
    fun whenIntentReceivedIfUrlIsFromUseOurAppUrlThenRegisterTimestamp() = coroutinesTestRule.runBlocking {
        givenUseOurAppCtaHasBeenSeen()
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, USE_OUR_APP_SHORTCUT_URL)
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockUserEventsStore).registerUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
    }

    @Test
    fun whenIntentReceivedIfUrlIsFromUseOurAppUrlThenFirePixel() {
        givenUseOurAppCtaHasBeenSeen()
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, USE_OUR_APP_SHORTCUT_URL)
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockPixel).fire(Pixel.PixelName.USE_OUR_APP_SHORTCUT_ADDED)
    }

    @Test
    fun whenIntentReceivedIfUrlIsNotFromUseOurAppUrlThenDoNotRegisterEvent() = coroutinesTestRule.runBlocking {
        givenUseOurAppCtaHasBeenSeen()
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, "www.example.com")
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockUserEventsStore, never()).registerUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
    }

    @Test
    fun whenIntentReceivedIfUrlIsNotFromUseOurAppUrlThenFireShortcutAddedPixel() {
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, "www.example.com")
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockPixel).fire(Pixel.PixelName.SHORTCUT_ADDED)
    }

    @Test
    fun whenIntentReceivedAndCtaNotSeenIfUrlIsFromUseOurAppUrlThenDoNotFireUseOurAppPixelAndFireShortcutPixel() = coroutinesTestRule.runBlocking {
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, USE_OUR_APP_SHORTCUT_URL)

        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockPixel, never()).fire(Pixel.PixelName.USE_OUR_APP_SHORTCUT_ADDED)
        verify(mockPixel).fire(Pixel.PixelName.SHORTCUT_ADDED)
    }

    private fun givenUseOurAppCtaHasBeenSeen() {
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP)).thenReturn(true)
    }
}
