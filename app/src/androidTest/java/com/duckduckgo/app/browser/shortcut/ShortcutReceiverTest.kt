/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
        testee = ShortcutReceiver()
        testee.pixel = mockPixel
        testee.dispatcher = coroutinesTestRule.testDispatcherProvider
        testee.appCoroutineScope = TestScope()
    }

    @Test
    fun whenIntentReceivedThenFireShortcutAddedPixel() {
        val intent = Intent()
        intent.putExtra(ShortcutBuilder.SHORTCUT_URL_ARG, "www.example.com")
        intent.putExtra(ShortcutBuilder.SHORTCUT_TITLE_ARG, "Title")
        testee.onShortcutAdded(null, intent)

        verify(mockPixel).fire(AppPixelName.SHORTCUT_ADDED)
    }
}
