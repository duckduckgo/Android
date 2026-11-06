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

package com.duckduckgo.app.browser.tabs.transition

import android.content.Context
import android.view.View
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class WindowCaptureTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun windowWithNoWidthHasNoPicture() = runTest {
        assertNull(windowSized(width = 0, height = 200).capturePicture())
    }

    @Test
    fun windowWithNoHeightHasNoPicture() = runTest {
        assertNull(windowSized(width = 100, height = 0).capturePicture())
    }

    private fun windowSized(
        width: Int,
        height: Int,
    ): Window {
        val decorView = View(context).apply { layout(0, 0, width, height) }
        return mock { on { this.decorView } doReturn decorView }
    }
}
