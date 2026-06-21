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

package com.duckduckgo.app.fire.clearing

import android.content.Context
import android.content.pm.ApplicationInfo
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class WebViewDirectoriesDataClearingPluginTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val applicationInfo: ApplicationInfo = mock()
    private val context: Context = mock()
    private val fileDeleter: FileDeleter = mock()
    private val testee = WebViewDirectoriesDataClearingPlugin(context, fileDeleter)

    @Test fun whenFireBrowserDataThenWipesFireProfileDirExceptCookiesAndLocalStorage() = runTest {
        val dataDir = "/data/app"
        applicationInfo.dataDir = dataDir
        whenever(context.applicationInfo).thenReturn(applicationInfo)
        whenever(fileDeleter.deleteContents(any(), any())).thenReturn(Result.success(Unit))

        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.FIRE)))

        verify(fileDeleter).deleteContents(
            eq(File(dataDir, "app_webview/Profiles/Fire")),
            eq(listOf("Cookies", "Local Storage")),
        )
    }

    @Test fun whenBrowserDataAllThenWipesFireProfileDir() = runTest {
        applicationInfo.dataDir = "/data/app"
        whenever(context.applicationInfo).thenReturn(applicationInfo)
        whenever(fileDeleter.deleteContents(any(), any())).thenReturn(Result.success(Unit))
        testee.onClearData(setOf(ClearableData.BrowserData.All))
        verify(fileDeleter).deleteContents(eq(File("/data/app", "app_webview/Profiles/Fire")), eq(listOf("Cookies", "Local Storage")))
    }

    @Test fun whenRegularThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.REGULAR)))
        verify(fileDeleter, never()).deleteContents(any(), any())
    }
}
