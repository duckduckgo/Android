/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser

import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckDuckGoWebViewTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckDuckGoWebView

    @Before
    @UiThreadTest
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testee = DuckDuckGoWebView(context)
        testee.dispatcherProvider = coroutineRule.testDispatcherProvider
    }

    @Test
    @UiThreadTest
    fun whenWebViewInitialisedThenSafeBrowsingDisabled() {
        assertFalse(testee.settings.safeBrowsingEnabled)
    }
}
