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
import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShareLink
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShowCopiedNotification
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class SyncGetOnOtherPlatformsViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val pixelMock: Pixel = mock()
    private val clipboardInteractorMock: ClipboardInteractor = mock()
    private val fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    private fun createViewModel(): SyncGetOnOtherPlatformsViewModel {
        return SyncGetOnOtherPlatformsViewModel(
            pixel = pixelMock,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            clipboardInteractor = clipboardInteractorMock,
            settingsPageFeature = fakeSettingsPageFeature,
        )
    }

    @Test
    fun whenDesktopBrowserFeatureEnabledThenViewStateShowsDesktopBrowserUrl() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))

        val testee = createViewModel()

        testee.viewState.test {
            assertTrue(awaitItem().showDesktopBrowserUrl)
        }
    }

    @Test
    fun whenDesktopBrowserFeatureDisabledThenViewStateDoesNotShowDesktopBrowserUrl() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))

        val testee = createViewModel()

        testee.viewState.test {
            assertFalse(awaitItem().showDesktopBrowserUrl)
        }
    }

    @Test
    fun whenShareClickedAndFeatureEnabledThenShareDesktopBrowserLink() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))
        val testee = createViewModel()

        testee.commands.test {
            testee.onShareClicked(null)

            val command = awaitItem() as ShareLink
            assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_android_sync", command.link)
        }
    }

    @Test
    fun whenShareClickedAndFeatureDisabledThenShareAppLink() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))
        val testee = createViewModel()

        testee.commands.test {
            testee.onShareClicked(null)

            val command = awaitItem() as ShareLink
            assertEquals("https://duckduckgo.com/app?origin=funnel_browser_android_sync", command.link)
        }
    }

    @Test
    fun whenLinkClickedAndFeatureEnabledThenCopyDesktopBrowserLink() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel()

        testee.onLinkClicked(null)

        verify(clipboardInteractorMock).copyToClipboard(
            eq("https://duckduckgo.com/browser?origin=funnel_browser_android_sync"),
            eq(false),
        )
    }

    @Test
    fun whenLinkClickedAndFeatureDisabledThenCopyAppLink() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel()

        testee.onLinkClicked(null)

        verify(clipboardInteractorMock).copyToClipboard(
            eq("https://duckduckgo.com/app?origin=funnel_browser_android_sync"),
            eq(false),
        )
    }

    @Test
    fun whenLinkClickedAndSystemShowsNotificationThenDoNotEmitShowCopiedNotificationCommand() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)
        val testee = createViewModel()

        testee.commands.test {
            testee.onLinkClicked(null)
            expectNoEvents()
        }
    }

    @Test
    fun whenLinkClickedAndSystemDoesNotShowNotificationThenEmitShowCopiedNotificationCommand() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(false)
        val testee = createViewModel()

        testee.commands.test {
            testee.onLinkClicked(null)

            assertEquals(ShowCopiedNotification, awaitItem())
        }
    }
}
