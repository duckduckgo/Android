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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ShortcutReceiverTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private lateinit var testee: ShortcutReceiver

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        testee = ShortcutReceiver(context, mockPixel, coroutinesTestRule.testDispatcherProvider, TestCoroutineScope())
    }

    @Test
    fun whenIntentReceivedThenFireShortcutAddedPixel() {
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, "www.example.com")
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onReceive(null, intent)

        verify(mockPixel).fire(AppPixelName.SHORTCUT_ADDED)
    }
}
