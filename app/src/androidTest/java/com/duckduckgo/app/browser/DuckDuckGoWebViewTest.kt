/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.os.Build
import android.support.test.InstrumentationRegistry
import android.support.test.annotation.UiThreadTest
import org.junit.Assert.assertFalse
import org.junit.Test

class DuckDuckGoWebViewTest {

    private lateinit var testee: DuckDuckGoWebView

    @UiThreadTest
    @Test
    fun whenWebViewInitialisedThenSafeBrowsingDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            testee = DuckDuckGoWebView(InstrumentationRegistry.getTargetContext())
            assertFalse(testee.settings.safeBrowsingEnabled)
        }
    }
}