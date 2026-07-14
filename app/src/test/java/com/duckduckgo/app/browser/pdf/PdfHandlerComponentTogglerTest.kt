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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PdfHandlerComponentTogglerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context: Context = mock()
    private val packageManager: PackageManager = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val pdfViewerToggle: Toggle = mock()
    private val externalPdfHandlerToggle: Toggle = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private val appId = "com.duckduckgo.mobile.android"

    private lateinit var testee: PdfHandlerComponentToggler

    @Before
    fun setUp() {
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(appBuildConfig.applicationId).thenReturn(appId)
        whenever(androidBrowserConfigFeature.pdfViewer()).thenReturn(pdfViewerToggle)
        whenever(androidBrowserConfigFeature.externalPdfHandler()).thenReturn(externalPdfHandlerToggle)
        whenever(externalPdfHandlerToggle.isEnabled()).thenReturn(true)

        testee = PdfHandlerComponentToggler(
            context = context,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            appBuildConfig = appBuildConfig,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when sdk is 31 and flag is on then enables alias`() {
        whenever(appBuildConfig.sdkInt).thenReturn(31)
        whenever(pdfViewerToggle.isEnabled()).thenReturn(true)

        testee.sync()

        val componentCaptor = argumentCaptor<ComponentName>()
        val stateCaptor = argumentCaptor<Int>()
        val flagsCaptor = argumentCaptor<Int>()
        verify(packageManager).setComponentEnabledSetting(componentCaptor.capture(), stateCaptor.capture(), flagsCaptor.capture())
        assertEquals(appId, componentCaptor.firstValue.packageName)
        assertEquals("com.duckduckgo.app.dispatchers.PdfViewerHandler", componentCaptor.firstValue.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, stateCaptor.firstValue)
        assertEquals(PackageManager.DONT_KILL_APP, flagsCaptor.firstValue)
    }

    @Test
    fun `when sdk is 30 and flag is on then disables alias`() {
        whenever(appBuildConfig.sdkInt).thenReturn(30)
        whenever(pdfViewerToggle.isEnabled()).thenReturn(true)

        testee.sync()

        val componentCaptor = argumentCaptor<ComponentName>()
        val stateCaptor = argumentCaptor<Int>()
        val flagsCaptor = argumentCaptor<Int>()
        verify(packageManager).setComponentEnabledSetting(componentCaptor.capture(), stateCaptor.capture(), flagsCaptor.capture())
        assertEquals(appId, componentCaptor.firstValue.packageName)
        assertEquals("com.duckduckgo.app.dispatchers.PdfViewerHandler", componentCaptor.firstValue.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateCaptor.firstValue)
        assertEquals(PackageManager.DONT_KILL_APP, flagsCaptor.firstValue)
    }

    @Test
    fun `when sdk is 33 and flag is off then disables alias`() {
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(pdfViewerToggle.isEnabled()).thenReturn(false)

        testee.sync()

        val componentCaptor = argumentCaptor<ComponentName>()
        val stateCaptor = argumentCaptor<Int>()
        val flagsCaptor = argumentCaptor<Int>()
        verify(packageManager).setComponentEnabledSetting(componentCaptor.capture(), stateCaptor.capture(), flagsCaptor.capture())
        assertEquals(appId, componentCaptor.firstValue.packageName)
        assertEquals("com.duckduckgo.app.dispatchers.PdfViewerHandler", componentCaptor.firstValue.className)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateCaptor.firstValue)
        assertEquals(PackageManager.DONT_KILL_APP, flagsCaptor.firstValue)
    }

    @Test
    fun `when sdk is 33 and pdfViewer is on but externalPdfHandler is off then disables alias`() {
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(pdfViewerToggle.isEnabled()).thenReturn(true)
        whenever(externalPdfHandlerToggle.isEnabled()).thenReturn(false)

        testee.sync()

        val stateCaptor = argumentCaptor<Int>()
        verify(packageManager).setComponentEnabledSetting(any(), stateCaptor.capture(), any())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, stateCaptor.firstValue)
    }
}
