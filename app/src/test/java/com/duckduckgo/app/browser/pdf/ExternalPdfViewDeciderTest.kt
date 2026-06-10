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

package com.duckduckgo.app.browser.pdf

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ExternalPdfViewDeciderTest {

    private val appBuildConfig: AppBuildConfig = mock()

    private val testee: ExternalPdfViewDecider by lazy { RealExternalPdfViewDecider(appBuildConfig) }

    @Test
    fun whenUriIsNullThenNothingToRender() {
        whenever(appBuildConfig.sdkInt).thenReturn(34)

        assertEquals(ExternalPdfViewDecision.NothingToRender, testee.decideForView(null))
    }

    @Test
    fun whenSdkSupportsViewerThenRender() {
        whenever(appBuildConfig.sdkInt).thenReturn(31)
        val uri = "content://media/external/file/doc.pdf".toUri()

        assertEquals(ExternalPdfViewDecision.Render(uri), testee.decideForView(uri))
    }

    @Test
    fun whenSdkAboveMinimumThenRender() {
        whenever(appBuildConfig.sdkInt).thenReturn(35)
        val uri = "file:///sdcard/doc.pdf".toUri()

        assertEquals(ExternalPdfViewDecision.Render(uri), testee.decideForView(uri))
    }

    @Test
    fun whenSdkBelowMinimumThenDelegateToOtherApps() {
        whenever(appBuildConfig.sdkInt).thenReturn(30)
        val uri = "content://media/external/file/doc.pdf".toUri()

        assertEquals(ExternalPdfViewDecision.DelegateToOtherApps(uri), testee.decideForView(uri))
    }
}
