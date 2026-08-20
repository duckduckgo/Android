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

package com.duckduckgo.sync.impl.promotion

import android.annotation.SuppressLint
import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionParams
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_ENABLED
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealSyncDesktopAppPromotionLauncherTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val globalActivityStarterMock: GlobalActivityStarter = mock()
    private val contextMock: Context = mock()
    private val fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    private val testee = RealSyncDesktopAppPromotionLauncher(
        globalActivityStarter = globalActivityStarterMock,
        settingsPageFeature = fakeSettingsPageFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenDesktopBrowserPromoDisabledThenSyncOwnScreenIsLaunched() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))

        testee.launch(contextMock, SOURCE_SYNC_ENABLED)

        verify(globalActivityStarterMock).start(
            eq(contextMock),
            eq(SyncGetOnOtherPlatformsParams(SOURCE_SYNC_ENABLED)),
            anyOrNull(),
        )
    }

    @Test
    fun whenDesktopBrowserPromoEnabledThenSharedPromoScreenIsLaunchedWithSyncAttribution() = runTest {
        givenPromoEnabled()

        testee.launch(contextMock, SOURCE_SYNC_ENABLED)

        assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_android_sync", capturedParams().downloadUrl)
    }

    @Test
    fun whenSharedPromoScreenIsLaunchedThenSyncPixelNamesAndSourceAreUnchanged() = runTest {
        givenPromoEnabled()

        testee.launch(contextMock, SOURCE_SYNC_ENABLED)

        val expectedSource = mapOf("source" to "activated")
        with(capturedParams().pixels) {
            assertEquals("sync_get_other_devices", impression?.pixelName)
            assertEquals(expectedSource, impression?.parameters)
            assertEquals("sync_get_other_devices_share", shareClicked?.pixelName)
            assertEquals(expectedSource, shareClicked?.parameters)
            assertEquals("sync_get_other_devices_copy", linkClicked?.pixelName)
            assertEquals(expectedSource, linkClicked?.parameters)
            assertEquals(null, dismissed)
        }
    }

    @Test
    fun whenSharedPromoScreenIsLaunchedThenNoDismissButtonAndNoInteractionHandler() = runTest {
        givenPromoEnabled()

        testee.launch(contextMock, SOURCE_SYNC_ENABLED)

        val params = capturedParams()
        assertEquals(false, params.showDismissButton)
        assertEquals(null, params.handlerId)
    }

    private fun givenPromoEnabled() {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))
        whenever(contextMock.getString(any())).thenReturn("copy")
    }

    private fun capturedParams(): DesktopAppPromotionParams {
        val captor = argumentCaptor<ActivityParams>()
        verify(globalActivityStarterMock).start(eq(contextMock), captor.capture(), anyOrNull())
        return captor.firstValue as DesktopAppPromotionParams
    }
}
