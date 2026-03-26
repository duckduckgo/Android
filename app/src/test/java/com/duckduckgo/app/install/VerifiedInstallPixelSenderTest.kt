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

package com.duckduckgo.app.install

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class VerifiedInstallPixelSenderTest {

    @get:Rule
    @Suppress("unused")
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val isVerifiedPlayStoreInstall: IsVerifiedPlayStoreInstall = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val pixel: Pixel = mock()

    private val verifiedInstallDataStore: VerifiedInstallDataStore = mock()

    private val androidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val paramsCaptor = argumentCaptor<Map<String, String>>()

    private lateinit var testee: VerifiedInstallPixelSender

    @Before
    fun setUp() {
        configureFeatureFlag(enabled = true)

        testee = VerifiedInstallPixelSender(
            isVerifiedPlayStoreInstall = isVerifiedPlayStoreInstall,
            appBuildConfig = appBuildConfig,
            pixel = pixel,
            verifiedInstallDataStore = verifiedInstallDataStore,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `install - when feature flag disabled then no pixel is fired`() = runTest {
        configureFeatureFlag(enabled = false)
        configureAsNewVerifiedInstall()

        testee.onAppAtbInitialized()

        verifyNoVerifiedInstallPixelsFired()
    }

    @Test
    fun `install - when not verified install then no pixel is fired`() = runTest {
        configureAsNotVerifiedInstall()

        testee.onAppAtbInitialized()

        verifyNoVerifiedInstallPixelsFired()
    }

    @Test
    fun `install - when verified install and no stored version then install pixel is fired`() = runTest {
        configureAsNewVerifiedInstall()

        testee.onAppAtbInitialized()

        verifyInstallPixelFired(returningUser = false)
        verify(verifiedInstallDataStore).setLastInstalledVersion(123)
    }

    @Test
    fun `install - when verified install with returning user then install pixel is fired with returning user true`() = runTest {
        configureAsNewVerifiedInstall(returningUser = true)

        testee.onAppAtbInitialized()

        verifyInstallPixelFired(returningUser = true)
        verify(verifiedInstallDataStore).setLastInstalledVersion(123)
    }

    @Test
    fun `install - when already has stored version then no pixel is fired`() = runTest {
        configureAsExistingVerifiedInstall(lastVersion = 123, currentVersion = 123)

        testee.onAppAtbInitialized()

        verifyNoVerifiedInstallPixelsFired()
    }

    @Test
    fun `update - when feature flag disabled then no pixel is fired`() = runTest {
        configureFeatureFlag(enabled = false)
        configureAsExistingVerifiedInstall(lastVersion = 123, currentVersion = 124)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyNoVerifiedInstallPixelsFired()
    }

    @Test
    fun `update - when not verified install then no pixel is fired`() = runTest {
        configureAsNotVerifiedInstall()

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyNoVerifiedInstallPixelsFired()
    }

    @Test
    fun `update - when same version then no pixel is fired`() = runTest {
        configureAsExistingVerifiedInstall(lastVersion = 123, currentVersion = 123)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyNoVerifiedInstallPixelsFired()
        verify(verifiedInstallDataStore, never()).setLastInstalledVersion(any())
    }

    @Test
    fun `update - when no stored version then stores current version without firing pixel`() = runTest {
        configureAsExistingUserWithNoStoredVersion(currentVersion = 124)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyNoVerifiedInstallPixelsFired()
        verify(verifiedInstallDataStore).setLastInstalledVersion(124)
    }

    @Test
    fun `update - when different version then update pixel is fired`() = runTest {
        configureAsExistingVerifiedInstall(lastVersion = 123, currentVersion = 124)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyUpdatePixelFired(returningUser = false)
        verify(verifiedInstallDataStore).setLastInstalledVersion(124)
    }

    @Test
    fun `update - when different version with returning user then update pixel is fired with returning user true`() = runTest {
        configureAsExistingVerifiedInstall(lastVersion = 123, currentVersion = 124, returningUser = true)

        testee.onAppRetentionAtbRefreshed("old", "new")

        verifyUpdatePixelFired(returningUser = true)
        verify(verifiedInstallDataStore).setLastInstalledVersion(124)
    }

    private fun verifyInstallPixelFired(returningUser: Boolean) {
        verify(pixel).fire(
            eq(AppPixelName.APP_INSTALL_VERIFIED_INSTALL),
            paramsCaptor.capture(),
            eq(emptyMap()),
            eq(Pixel.PixelType.Unique()),
        )

        val params = paramsCaptor.firstValue
        assert(params["returning_user"] == returningUser.toString())
    }

    private fun verifyUpdatePixelFired(returningUser: Boolean) {
        verify(pixel).fire(
            eq(AppPixelName.APP_UPDATE_VERIFIED_INSTALL),
            paramsCaptor.capture(),
            eq(emptyMap()),
            eq(Pixel.PixelType.Count),
        )

        val params = paramsCaptor.firstValue
        assert(params["returning_user"] == returningUser.toString())
    }

    private fun verifyNoVerifiedInstallPixelsFired() {
        verifyNoInteractions(pixel)
    }

    private fun configureFeatureFlag(enabled: Boolean) {
        androidBrowserConfigFeature.sendVerifiedInstallPixels().setRawStoredState(Toggle.State(enable = enabled))
    }

    private fun configureAsNotVerifiedInstall() {
        whenever(isVerifiedPlayStoreInstall()).thenReturn(false)
    }

    private suspend fun configureAsNewVerifiedInstall(returningUser: Boolean = false) {
        whenever(isVerifiedPlayStoreInstall()).thenReturn(true)
        whenever(appBuildConfig.versionCode).thenReturn(123)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(returningUser)
        whenever(verifiedInstallDataStore.getLastInstalledVersion()).thenReturn(null)
    }

    private suspend fun configureAsExistingVerifiedInstall(
        lastVersion: Int,
        currentVersion: Int,
        returningUser: Boolean = false,
    ) {
        whenever(isVerifiedPlayStoreInstall()).thenReturn(true)
        whenever(appBuildConfig.versionCode).thenReturn(currentVersion)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(returningUser)
        whenever(verifiedInstallDataStore.getLastInstalledVersion()).thenReturn(lastVersion)
    }

    private suspend fun configureAsExistingUserWithNoStoredVersion(currentVersion: Int) {
        whenever(isVerifiedPlayStoreInstall()).thenReturn(true)
        whenever(appBuildConfig.versionCode).thenReturn(currentVersion)
        whenever(verifiedInstallDataStore.getLastInstalledVersion()).thenReturn(null)
    }
}
