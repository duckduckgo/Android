/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.webview.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.api.WebViewProfileManager
import com.duckduckgo.common.test.CoroutineTestRule
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DefaultWebStorageProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val webViewProfileManager: WebViewProfileManager = mock()
    private val lazyWebViewProfileManager: Lazy<WebViewProfileManager> = Lazy { webViewProfileManager }

    private lateinit var provider: DefaultWebStorageProvider

    @Before
    fun setup() {
        provider = DefaultWebStorageProvider(
            lazyWebViewProfileManager,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenProfileSwitchingNotAvailableThenReturnDefaultWebStorage() = runTest {
        whenever(webViewProfileManager.isProfileSwitchingAvailable()).thenReturn(false)

        val result = provider.get()

        assertNotNull(result)
    }

    @Test
    fun whenProfileNameIsEmptyThenReturnDefaultWebStorage() = runTest {
        whenever(webViewProfileManager.isProfileSwitchingAvailable()).thenReturn(true)
        whenever(webViewProfileManager.getCurrentProfileName()).thenReturn("")

        val result = provider.get()

        assertNotNull(result)
    }
}
