/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ObsoleteCoroutinesApi
@ExperimentalTime
class WelcomePageViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var pixel: Pixel

    @Mock
    private lateinit var appInstallStore: AppInstallStore

    @Mock
    private lateinit var defaultRoleBrowserDialog: DefaultRoleBrowserDialog

    @Mock
    private lateinit var mockNotificationPermissionsFeatureToggles: NotificationPermissionsFeatureToggles

    private val events = MutableSharedFlow<WelcomePageView.Event>(replay = 1)

    private lateinit var viewModel: WelcomePageViewModel

    private lateinit var viewEvents: Flow<WelcomePageView.State>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = WelcomePageViewModel(
            appInstallStore = appInstallStore,
            context = mock(),
            pixel = pixel,
            defaultRoleBrowserDialog = defaultRoleBrowserDialog,
            notificationPermissionsFeatureToggles = mockNotificationPermissionsFeatureToggles,
        )

        viewEvents = events.flatMapLatest { viewModel.reduce(it) }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldNotShowDialogThenFireAndFinish() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        events.emit(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
        }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowDialogAndShowThenFireAndEmitShowDialog() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        val intent = Intent()
        whenever(defaultRoleBrowserDialog.createIntent(any())).thenReturn(intent)

        events.emit(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.ShowDefaultBrowserDialog(intent))
        }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowDialogNullIntentThenFireAndFinish() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(defaultRoleBrowserDialog.createIntent(any())).thenReturn(null)

        events.emit(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(pixel).fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
        }
    }

    @Test
    fun whenOnDefaultBrowserSetThenCallDialogShownFireAndFinish() = runTest {
        events.emit(WelcomePageView.Event.OnDefaultBrowserSet)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(defaultRoleBrowserDialog).dialogShown()
            verify(pixel).fire(
                AppPixelName.DEFAULT_BROWSER_SET,
                mapOf(
                    Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
                ),
            )
        }
    }

    @Test
    fun whenOnDefaultBrowserNotSetThenCallDialogShownFireAndFinish() = runTest {
        events.emit(WelcomePageView.Event.OnDefaultBrowserNotSet)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(defaultRoleBrowserDialog).dialogShown()
            verify(pixel).fire(
                AppPixelName.DEFAULT_BROWSER_NOT_SET,
                mapOf(
                    Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
                ),
            )
        }
    }

    @Test
    fun givenExperimentalVariantWhenOnNotificationPermissionsRequestedThenFireAndEmitWelcomeAnimation() = runTest {
        val mockToggle: Toggle = mock { on { isEnabled() } doReturn true }
        whenever(mockNotificationPermissionsFeatureToggles.noPermissionsPrompt()).thenReturn(mockToggle)

        events.emit(WelcomePageView.Event.OnNotificationPermissionsRequested)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.ShowWelcomeAnimation)
        }
    }

    @Test
    fun givenControlVariantWhenOnNotificationPermissionsRequestedThenFireAndEmitShowNotificationsPermissionsPrompt() = runTest {
        val mockToggle: Toggle = mock { on { isEnabled() } doReturn false }
        whenever(mockNotificationPermissionsFeatureToggles.noPermissionsPrompt()).thenReturn(mockToggle)

        events.emit(WelcomePageView.Event.OnNotificationPermissionsRequested)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.ShowNotificationsPermissionsPrompt)
        }
    }
}
