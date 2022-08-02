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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class EvalMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockCallback: AutoconsentCallback = mock()
    private val mockWebView: WebView = mock()

    val testee = EvalMessageHandlerPlugin(coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    @Test
    fun whenEvalMessageIfTypeNotEvalDoNothing() = runTest {
        testee.process("noMatching", "", mockWebView, mockCallback)

        verifyNoInteractions(mockWebView)
    }
}
