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

package com.duckduckgo.mobile.android.ui.notifyme

import android.os.Build
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@Config(manifest = Config.NONE)
class NotifyMeViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAppBuildConfig = mock<AppBuildConfig>()
    private val mockListener = mock<NotifyMeListener>()
    private val mockLifecycleOwner = mock<LifecycleOwner>()

    private val testee: NotifyMeViewModel by lazy {
        NotifyMeViewModel(appBuildConfig = mockAppBuildConfig)
    }

    @Test
    fun whenNotificationsNotAllowedAndDismissNotCalledAndViewIsNotDismissedThenViewIsVisible() = runTest {
        setup(
            notificationsAllowed = false,
            dismissCalled = false,
            viewDismissed = false,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.visible)
        }
    }

    @Test
    fun whenNotificationsNotAllowedAndDismissNotCalledAndViewIsDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = false,
            dismissCalled = false,
            viewDismissed = true,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsNotAllowedAndDismissCalledAndViewIsNotDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = false,
            dismissCalled = true,
            viewDismissed = false,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsNotAllowedAndDismissCalledAndViewIsDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = false,
            dismissCalled = true,
            viewDismissed = true,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsAllowedAndDismissNotCalledAndViewIsNotDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = true,
            dismissCalled = false,
            viewDismissed = false,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsAllowedAndDismissNotCalledAndViewIsDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = true,
            dismissCalled = false,
            viewDismissed = true,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsAllowedAndDismissCalledAndViewIsNotDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = true,
            dismissCalled = true,
            viewDismissed = false,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenNotificationsAllowedAndDismissCalledAndViewIsDismissedThenViewIsNotVisible() = runTest {
        setup(
            notificationsAllowed = true,
            dismissCalled = true,
            viewDismissed = true,
        )

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visible)
        }
    }

    @Test
    fun whenOnResumeCalledThenCheckPermissionsCommandIsSent() = runTest {
        testee.onResume(mockLifecycleOwner)

        testee.commands().test {
            assertEquals(
                NotifyMeViewModel.Command.UpdateNotificationsState,
                awaitItem(),
            )
        }
    }

    @Test
    fun whenOnNotifyMeButtonClickedWithShouldShowRequestPermissionRationaleTrueThenShowPermissionRationaleCommandIsSent() = runTest {
        val shouldShowRequestPermissionRationale = true

        testee.onNotifyMeButtonClicked(shouldShowRequestPermissionRationale)

        testee.commands().test {
            assertEquals(
                NotifyMeViewModel.Command.ShowPermissionRationale,
                awaitItem(),
            )
        }
    }

    @Test
    fun whenOnNotifyMeButtonClickedWithShouldShowRequestPermissionRationaleFalseThenOpenSettingsCommandIsSent() = runTest {
        val shouldShowRequestPermissionRationale = false

        testee.onNotifyMeButtonClicked(shouldShowRequestPermissionRationale)

        testee.commands().test {
            assertEquals(
                NotifyMeViewModel.Command.OpenSettings,
                awaitItem(),
            )
        }
    }

    @Test
    fun whenOnCloseButtonClickedThenCloseCommandIsSentAndSetDismissedIsCalled() = runTest {
        testee.setNotifyMeListener(mockListener)

        testee.onCloseButtonClicked()

        testee.commands().test {
            verify(mockListener).setDismissed()
            assertEquals(
                NotifyMeViewModel.Command.DismissComponent,
                awaitItem(),
            )
        }
    }

    @Test
    fun whenOnResumeCalledForAndroid13PlusThenUpdateNotificationsStateOnAndroid13PlusCommandIsSent() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.TIRAMISU)

        testee.onResume(mockLifecycleOwner)

        testee.commands().test {
            assertEquals(
                NotifyMeViewModel.Command.UpdateNotificationsStateOnAndroid13Plus,
                awaitItem(),
            )
        }
    }

    @Test
    fun whenOnResumeCalledForAndroid12OrBelowThenUpdateNotificationsStateCommandIsSent() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.S)

        testee.onResume(mockLifecycleOwner)

        testee.commands().test {
            assertEquals(
                NotifyMeViewModel.Command.UpdateNotificationsState,
                awaitItem(),
            )
        }
    }

    private fun setup(notificationsAllowed: Boolean, dismissCalled: Boolean, viewDismissed: Boolean) {
        testee.setNotifyMeListener(mockListener)
        testee.updateNotificationsPermissions(notificationsAllowed)
        if (dismissCalled) {
            testee.onCloseButtonClicked()
        }
        whenever(mockListener.isDismissed()).thenReturn(viewDismissed)
    }
}
