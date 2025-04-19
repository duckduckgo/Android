/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.installation.impl.installer

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.installation.impl.installer.InstallationPixelName.APP_INSTALLER_FULL_PACKAGE_NAME
import com.duckduckgo.installation.impl.installer.InstallationPixelName.APP_INSTALLER_PACKAGE_NAME
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore.IncludedPackages
import com.duckduckgo.installation.impl.installer.fullpackage.feature.InstallSourceFullPackageFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class InstallSourcePrivacyConfigObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockPixel = mock<Pixel>()
    private val context = RuntimeEnvironment.getApplication()
    private val mockInstallSourceExtractor = mock<InstallSourceExtractor>()
    private val mockFullPackageFeatureStore: InstallSourceFullPackageStore = mock()
    private val fakeFeature = FakeFeatureToggleFactory.create(InstallSourceFullPackageFeature::class.java)

    private val testee = InstallSourcePrivacyConfigObserver(
        context = context,
        pixel = mockPixel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
        installSourceExtractor = mockInstallSourceExtractor,
        store = mockFullPackageFeatureStore,
        installSourceFullPackageFeature = fakeFeature,
    )

    @Before
    @SuppressLint("DenyListedApi")
    fun setup() {
        fakeFeature.self().setRawStoredState(State(enable = true))
        whenever(mockInstallSourceExtractor.extract()).thenReturn("app.installer.package")
    }

    @Test
    fun whenNotPreviouslyProcessedThenPixelSent() = runTest {
        testee.onPrivacyConfigDownloaded()
        verify(mockPixel).fire(eq(APP_INSTALLER_PACKAGE_NAME), any(), any(), eq(Count))
    }

    @Test
    fun whenPreviouslyProcessedThenPixelNotSent() = runTest {
        testee.recordInstallSourceProcessed()
        testee.onPrivacyConfigDownloaded()
        verify(mockPixel, never()).fire(eq(APP_INSTALLER_PACKAGE_NAME), any(), any(), eq(Count))
    }

    @Test
    fun whenInstallerPackageIsInIncludedListThenFiresInstallerPackagePixel() = runTest {
        configurePackageIsMatching()
        testee.onPrivacyConfigDownloaded()
        verify(mockPixel).fire(eq(APP_INSTALLER_FULL_PACKAGE_NAME), any(), any(), eq(Count))
    }

    @Test
    fun whenInstallerPackageIsNotInIncludedListDoesNotFirePixel() = runTest {
        configurePackageNotMatching()
        testee.onPrivacyConfigDownloaded()
        verify(mockPixel, never()).fire(eq(APP_INSTALLER_FULL_PACKAGE_NAME), any(), any(), eq(Count))
    }

    @Test
    fun whenInstallerPackageIsNotInIncludedListButListContainsWildcardThenDoesFirePixel() = runTest {
        configureListHasWildcard()
        testee.onPrivacyConfigDownloaded()
        verify(mockPixel).fire(eq(APP_INSTALLER_FULL_PACKAGE_NAME), any(), any(), eq(Count))
    }

    private suspend fun configurePackageIsMatching() {
        whenever(mockFullPackageFeatureStore.getInstallSourceFullPackages()).thenReturn(IncludedPackages(listOf("app.installer.package")))
    }

    private suspend fun configureListHasWildcard() {
        whenever(mockFullPackageFeatureStore.getInstallSourceFullPackages()).thenReturn(IncludedPackages(listOf("*")))
    }

    private suspend fun configurePackageNotMatching() {
        whenever(mockFullPackageFeatureStore.getInstallSourceFullPackages()).thenReturn(
            IncludedPackages(
                listOf(
                    "this.will.not.match",
                    "nor.will.this",
                ),
            ),
        )
    }
}
