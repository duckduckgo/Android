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

package com.duckduckgo.app.fire

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.Test

class DataClearerForegroundAppRestartPixelTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pixel = mock<Pixel>()
    private val testee = DataClearerForegroundAppRestartPixel(context, pixel)

    @Test
    fun whenAppRestartsAfterOpenSearchWidgetThenPixelWithIntentIsSent() {
        val intent = SystemSearchActivity.fromWidget(context)
        testee.registerIntent(intent)
        testee.incrementCount()

        testee.firePendingPixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_AUTO_RESTART_WITH_INTENT)
    }

    @Test
    fun whenAppRestartsAfterOpenExternalLinkThenPixelWithIntentIsSent() {
        val i = givenIntentWithData("https://example.com")
        testee.registerIntent(i)
        testee.incrementCount()

        testee.firePendingPixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_AUTO_RESTART_WITH_INTENT)
    }

    @Test
    fun whenAppRestartsAfterOpenAnEmptyIntentThenPixelIsSent() {
        val intent = givenEmptyIntent()
        testee.registerIntent(intent)
        testee.incrementCount()

        testee.firePendingPixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_AUTO_RESTART)
    }

    @Test
    fun whenAllUnsentPixelsAreFiredThenResetCounter() {
        val intent = givenEmptyIntent()
        testee.registerIntent(intent)
        testee.incrementCount()

        testee.firePendingPixels()
        testee.firePendingPixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_AUTO_RESTART)
    }

    @Test
    fun whenAppRestartedAfterGoingBackFromBackgroundThenPixelIsSent() {
        val intent = SystemSearchActivity.fromWidget(context)
        testee.registerIntent(intent)
        testee.onAppBackgrounded()
        testee.incrementCount()

        testee.firePendingPixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_AUTO_RESTART)
    }

    private fun givenEmptyIntent(): Intent = Intent(context, BrowserActivity::class.java)

    private fun givenIntentWithData(url: String) =
        Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
}
