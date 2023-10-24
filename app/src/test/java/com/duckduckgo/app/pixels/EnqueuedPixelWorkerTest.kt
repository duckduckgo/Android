/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.pixels

import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class EnqueuedPixelWorkerTest {
    private val workManager: WorkManager = mock()
    private val pixel: Pixel = mock()
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()

    private lateinit var enqueuedPixelWorker: EnqueuedPixelWorker

    @Before
    fun setup() {
        enqueuedPixelWorker = EnqueuedPixelWorker(
            workManager,
            { pixel },
            unsentForgetAllPixelStore,
            webViewVersionProvider,
            defaultBrowserDetector,
            androidBrowserConfigFeature,
        )
        setupRemoteConfig(browserEnabled = false, collectFullWebViewVersionEnabled = false)
    }

    @Test
    fun whenOnCreateAndPendingPixelCountClearDataThenScheduleWorkerToFireMf() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(2)
        enqueuedPixelWorker.onCreate(lifecycleOwner)

        verify(workManager).enqueueUniquePeriodicWork(
            eq("com.duckduckgo.pixels.enqueued.worker"),
            eq(ExistingPeriodicWorkPolicy.KEEP),
            any(),
        )
    }

    @Test
    fun whenOnCreateAndPendingPixelCountClearDataIsZeroThenDoNotFireMf() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(0)
        enqueuedPixelWorker.onCreate(lifecycleOwner)

        verify(pixel, never()).fire(AppPixelName.FORGET_ALL_EXECUTED)
    }

    @Test
    fun whenOnStartAndLaunchByFireActionThenDoNotSendAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel, never()).fire(AppPixelName.APP_LAUNCH)
    }

    @Test
    fun whenOnStartAndAppLaunchThenSendAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "false",
            ),
        )
    }

    @Test
    fun whenOnStartAndAppLaunchAndShouldCollectOnAppLaunchIsTrueThenSendAppLaunchPixelWithFullWebViewVersion() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(webViewVersionProvider.getFullVersion()).thenReturn("91.0.4472.101")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        setupRemoteConfig(browserEnabled = true, collectFullWebViewVersionEnabled = true)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "false",
                Pixel.PixelParameter.WEBVIEW_FULL_VERSION to "91.0.4472.101",
            ),
        )
    }

    @Test
    fun whenOnStartAndAppLaunchAndShouldCollectOnAppLaunchIsFalseThenNeverSendAppLaunchPixelWithFullWebViewVersion() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(webViewVersionProvider.getFullVersion()).thenReturn("91.0.4472.101")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        setupRemoteConfig(browserEnabled = false, collectFullWebViewVersionEnabled = false)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "false",
            ),
        )
        verify(webViewVersionProvider, never()).getFullVersion()
    }

    @Test
    fun whenOnStartAndLaunchByFireActionFollowedByAppLaunchThenSendOneAppLaunchPixel() {
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(unsentForgetAllPixelStore.lastClearTimestamp).thenReturn(System.currentTimeMillis())
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "false",
            ),
        )
    }

    private fun setupRemoteConfig(browserEnabled: Boolean, collectFullWebViewVersionEnabled: Boolean) {
        whenever(androidBrowserConfigFeature.self()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return browserEnabled
                }

                override fun setEnabled(state: Toggle.State) {
                    TODO("Not yet implemented")
                }

                override fun getRawStoredState(): Toggle.State? {
                    TODO("Not yet implemented")
                }
            },
        )

        whenever(androidBrowserConfigFeature.collectFullWebViewVersion()).thenReturn(
            object : Toggle {
                override fun isEnabled(): Boolean {
                    return collectFullWebViewVersionEnabled
                }

                override fun setEnabled(state: Toggle.State) {
                    TODO("Not yet implemented")
                }

                override fun getRawStoredState(): Toggle.State? {
                    TODO("Not yet implemented")
                }
            },
        )
    }
}
