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

package com.duckduckgo.app.anr.ndk

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anrs.api.CrashAnnotationContributor
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_NATIVE
import com.duckduckgo.app.anr.CrashPixel.APPLICATION_CRASH_NATIVE_HANDLER_REGISTERED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NativeCrashInitTest {

    private val mockCustomTabDetector: CustomTabDetector = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockNativeCrashFeature: NativeCrashFeature = mock()
    private val mockWebViewVersionProvider: WebViewVersionProvider = mock()
    private val mockPixel: Pixel = mock()
    private val mockCrashpadInitializer: CrashpadInitializer = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()
    private val mockToggle: Toggle = mock()
    private val emptyContributors: PluginPoint<CrashAnnotationContributor> = mock()

    @Before
    fun setup() {
        whenever(mockAppBuildConfig.versionName).thenReturn("1.0.0")
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(33)
        whenever(mockCustomTabDetector.isCustomTab()).thenReturn(false)
        whenever(mockWebViewVersionProvider.getMajorVersion()).thenReturn("120")
        whenever(mockWebViewVersionProvider.getPackageName()).thenReturn("com.google.android.webview")
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(mockNativeCrashFeature.nativeCrashReportsFullWebViewVersion()).thenReturn(mockToggle)
        whenever(emptyContributors.getPlugins()).thenReturn(emptyList())
        whenever(mockCrashpadInitializer.initialize(any(), any(), anyOrNull())).thenReturn(true)
    }

    // ── Process routing ────────────────────────────────────────────────────────

    @Test
    fun `onCreate initialises Crashpad in main process`() {
        buildNativeCrashInit(isMainProcess = true).onCreate(mockLifecycleOwner)
        verify(mockCrashpadInitializer).initialize(any(), any(), anyOrNull())
    }

    @Test
    fun `onCreate skips Crashpad init in secondary process`() {
        buildNativeCrashInit(isMainProcess = false).onCreate(mockLifecycleOwner)
        verify(mockCrashpadInitializer, never()).initialize(any(), any(), anyOrNull())
    }

    @Test
    fun `onVpnProcessCreated initialises Crashpad in secondary process`() {
        buildNativeCrashInit(isMainProcess = false).onVpnProcessCreated()
        verify(mockCrashpadInitializer).initialize(any(), any(), anyOrNull())
    }

    @Test
    fun `onVpnProcessCreated skips Crashpad init in main process`() {
        buildNativeCrashInit(isMainProcess = true).onVpnProcessCreated()
        verify(mockCrashpadInitializer, never()).initialize(any(), any(), anyOrNull())
    }

    @Test
    fun `onPirProcessCreated initialises Crashpad in secondary process`() {
        buildNativeCrashInit(isMainProcess = false).onPirProcessCreated()
        verify(mockCrashpadInitializer).initialize(any(), any(), anyOrNull())
    }

    @Test
    fun `onPirProcessCreated skips Crashpad init in main process`() {
        buildNativeCrashInit(isMainProcess = true).onPirProcessCreated()
        verify(mockCrashpadInitializer, never()).initialize(any(), any(), anyOrNull())
    }

    // ── Handler registered pixel ───────────────────────────────────────────────

    @Test
    fun `handler registered pixel fired when initializer returns true`() {
        whenever(mockCrashpadInitializer.initialize(any(), any(), anyOrNull())).thenReturn(true)
        buildNativeCrashInit().onCreate(mockLifecycleOwner)
        verify(mockPixel).fire(eq(APPLICATION_CRASH_NATIVE_HANDLER_REGISTERED), any(), any(), any())
    }

    @Test
    fun `handler registered pixel NOT fired when initializer returns false`() {
        whenever(mockCrashpadInitializer.initialize(any(), any(), anyOrNull())).thenReturn(false)
        buildNativeCrashInit().onCreate(mockLifecycleOwner)
        verify(mockPixel, never()).fire(eq(APPLICATION_CRASH_NATIVE_HANDLER_REGISTERED), any(), any(), any())
    }

    // ── onCrash pixel ─────────────────────────────────────────────────────────

    @Test
    fun `onCrash lambda fires APPLICATION_CRASH_NATIVE pixel`() {
        val onCrash = captureOnCrash()
        onCrash?.invoke()
        verify(mockPixel).enqueueFire(eq(APPLICATION_CRASH_NATIVE), any(), any(), any())
    }

    @Test
    fun `onCrash pixel params include version, process name, and customTab`() {
        whenever(mockAppBuildConfig.versionName).thenReturn("5.0.0")
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.FDROID)
        whenever(mockCustomTabDetector.isCustomTab()).thenReturn(true)

        val onCrash = captureOnCrash(processName = "com.example:vpn")
        onCrash?.invoke()

        verify(mockPixel).enqueueFire(
            eq(APPLICATION_CRASH_NATIVE),
            eq(mapOf("v" to "5.0.0-FDROID", "pn" to "com.example:vpn", "customTab" to "true")),
            any(),
            any(),
        )
    }

    @Test
    fun `onCrash lambda not invoked during normal init — only fires on crash`() {
        buildNativeCrashInit().onCreate(mockLifecycleOwner)
        verify(mockPixel, never()).enqueueFire(eq(APPLICATION_CRASH_NATIVE), any(), any(), any())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun captureOnCrash(processName: String = "com.example"): (() -> Unit)? {
        var captured: (() -> Unit)? = null
        whenever(mockCrashpadInitializer.initialize(any(), any(), anyOrNull())).thenAnswer { invocation ->
            captured = invocation.getArgument(2)
            true
        }
        buildNativeCrashInit(processName = processName).onCreate(mockLifecycleOwner)
        return captured
    }

    private fun buildNativeCrashInit(
        isMainProcess: Boolean = true,
        processName: String = "com.example",
    ) = NativeCrashInit(
        isMainProcess = isMainProcess,
        customTabDetector = mockCustomTabDetector,
        appBuildConfig = mockAppBuildConfig,
        nativeCrashFeature = mockNativeCrashFeature,
        webViewVersionProvider = mockWebViewVersionProvider,
        pixel = mockPixel,
        processName = processName,
        crashpadInitializer = mockCrashpadInitializer,
        crashAnnotationContributors = emptyContributors,
    )
}
