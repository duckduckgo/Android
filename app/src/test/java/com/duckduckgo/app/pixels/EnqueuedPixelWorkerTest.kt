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

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@SuppressLint("DenyListedApi")
class EnqueuedPixelWorkerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val workManager: WorkManager = mock()
    private val pixel: Pixel = mock()
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val customTabDetector: CustomTabDetector = mock()
    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val isVerifiedPlayStoreInstall: IsVerifiedPlayStoreInstall = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var enqueuedPixelWorker: EnqueuedPixelWorker

    @Before
    fun setup() {
        enqueuedPixelWorker = EnqueuedPixelWorker(
            workManager,
            { pixel },
            unsentForgetAllPixelStore,
            webViewVersionProvider,
            defaultBrowserDetector,
            customTabDetector,
            androidBrowserConfigFeature,
            isVerifiedPlayStoreInstall,
            appBuildConfig,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
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
        verify(pixel, never()).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU)
        verify(pixel, never()).fire(
            pixel = eq(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU_DAILY),
            type = eq(Pixel.PixelType.Daily()),
            parameters = any(),
            encodedParameters = any(),
        )
    }

    @Test
    fun whenOnStartAndAppLaunchThenSendAppLaunchPixels() {
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
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
            ),
        )
        verify(pixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU)
        verify(pixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU_DAILY, type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenAppLaunchWithOurProductionAppPackageThenIsFlaggedAsOurApp() {
        whenever(appBuildConfig.applicationId).thenReturn("com.duckduckgo.mobile.android")

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        val expectedParameters = mapOf(
            Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "true",
        )

        verify(pixel).fire(
            eq(AppPixelName.APP_LAUNCH),
            argThat { this.entries.containsAll(expectedParameters.entries) },
            any(),
            any(),
        )
    }

    @Test
    fun whenAppLaunchWithOurDebugAppPackageThenIsFlaggedAsOurApp() {
        whenever(appBuildConfig.applicationId).thenReturn("com.duckduckgo.mobile.android.debug")

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        val expectedParameters = mapOf(
            Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "true",
        )

        verify(pixel).fire(
            eq(AppPixelName.APP_LAUNCH),
            argThat { this.entries.containsAll(expectedParameters.entries) },
            any(),
            any(),
        )
    }

    @Test
    fun whenAppLaunchWithADifferentAppPackageThenIsFlaggedAsNotOurApp() {
        whenever(appBuildConfig.applicationId).thenReturn("not.our.app.package")

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        val expectedParameters = mapOf(
            Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
        )

        verify(pixel).fire(
            eq(AppPixelName.APP_LAUNCH),
            argThat { this.entries.containsAll(expectedParameters.entries) },
            any(),
            any(),
        )
    }

    @Test
    fun whenOnStartAndInCustomTabAndAppLaunchThenDoNotSendAppLaunchPixel() {
        whenever(customTabDetector.isCustomTab()).thenReturn(true)
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "true",
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
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
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
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
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
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
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
            ),
        )
    }

    @Test
    fun whenOnStartAndVerifiedAppLaunchThenSendVerifiedAppLaunchPixel() {
        whenever(isVerifiedPlayStoreInstall.invoke()).thenReturn(true)
        whenever(unsentForgetAllPixelStore.pendingPixelCountClearData).thenReturn(1)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("91")
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        enqueuedPixelWorker.onCreate(lifecycleOwner)
        enqueuedPixelWorker.onStart(lifecycleOwner)

        verify(pixel).fire(
            AppPixelName.APP_LAUNCH_VERIFIED_INSTALL,
            mapOf(
                Pixel.PixelParameter.WEBVIEW_VERSION to "91",
                Pixel.PixelParameter.DEFAULT_BROWSER to "false",
                Pixel.PixelParameter.IS_DUCKDUCKGO_PACKAGE to "false",
            ),
        )
    }

    @Test
    fun whenNoUnsentClearDataPixelsPendingThenPixelNotSent() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(emptyMap())
        enqueuedPixelWorker.submitUnsentFirePixels()
        verify(pixel, never()).fire(AppPixelName.FORGET_ALL_EXECUTED)
        verify(unsentForgetAllPixelStore, never()).resetCount(any())
    }

    @Test
    fun whenUnsentClearDataPixelsPendingThenPixelSent() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(mapOf(BrowserMode.REGULAR to 5))
        enqueuedPixelWorker.submitUnsentFirePixels()
        verify(pixel, times(5)).fire(AppPixelName.FORGET_ALL_EXECUTED, mapOf(Pixel.PixelParameter.BROWSER_MODE to "regular"))
    }

    @Test
    fun whenClearDataPixelsSentThenStoreCleared() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(mapOf(BrowserMode.REGULAR to 5))
        enqueuedPixelWorker.submitUnsentFirePixels()
        verify(unsentForgetAllPixelStore).resetCount(BrowserMode.REGULAR)
    }

    @Test
    fun whenNoUnsentClearDataPixelsPendingThenDailyPixelNotSent() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(emptyMap())
        enqueuedPixelWorker.submitUnsentFirePixels()
        verify(pixel, never()).fire(
            eq(AppPixelName.FORGET_ALL_EXECUTED_DAILY),
            any(),
            any(),
            eq(Pixel.PixelType.Daily()),
        )
    }

    @Test
    fun whenUnsentClearDataPixelsPendingThenDailyPixelSentExactlyOnce() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(mapOf(BrowserMode.REGULAR to 5))
        enqueuedPixelWorker.submitUnsentFirePixels()
        verify(pixel, times(1)).fire(
            AppPixelName.FORGET_ALL_EXECUTED_DAILY,
            mapOf(Pixel.PixelParameter.BROWSER_MODE to "regular"),
            emptyMap(),
            Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenUnsentFireModeClearDataPixelsPendingThenPixelsSentWithFireMode() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData).thenReturn(mapOf(BrowserMode.FIRE to 2))

        enqueuedPixelWorker.submitUnsentFirePixels()

        val params = mapOf(Pixel.PixelParameter.BROWSER_MODE to "fire")
        verify(pixel, times(2)).fire(AppPixelName.FORGET_ALL_EXECUTED, params)
        verify(pixel).fire(AppPixelName.FORGET_ALL_EXECUTED_DAILY, params, emptyMap(), Pixel.PixelType.Daily())
        verify(unsentForgetAllPixelStore).resetCount(BrowserMode.FIRE)
    }

    @Test
    fun whenBothModeClearDataPixelsPendingThenEachModeIsSubmittedAndCleared() = runTest {
        whenever(unsentForgetAllPixelStore.pendingPixelCountsClearData)
            .thenReturn(mapOf(BrowserMode.REGULAR to 1, BrowserMode.FIRE to 2))

        enqueuedPixelWorker.submitUnsentFirePixels()

        verify(pixel).fire(AppPixelName.FORGET_ALL_EXECUTED, mapOf(Pixel.PixelParameter.BROWSER_MODE to "regular"))
        verify(pixel, times(2)).fire(AppPixelName.FORGET_ALL_EXECUTED, mapOf(Pixel.PixelParameter.BROWSER_MODE to "fire"))
        verify(unsentForgetAllPixelStore).resetCount(BrowserMode.REGULAR)
        verify(unsentForgetAllPixelStore).resetCount(BrowserMode.FIRE)
    }

    private fun setupRemoteConfig(
        browserEnabled: Boolean,
        collectFullWebViewVersionEnabled: Boolean,
    ) {
        androidBrowserConfigFeature.self().setRawStoredState(State(enable = browserEnabled))
        androidBrowserConfigFeature.collectFullWebViewVersion().setRawStoredState(State(enable = collectFullWebViewVersionEnabled))
    }
}
