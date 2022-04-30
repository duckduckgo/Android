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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ObsoleteCoroutinesApi
@ExperimentalTime
@ExperimentalCoroutinesApi
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

    private val events = ConflatedBroadcastChannel<WelcomePageView.Event>()

    lateinit var viewModel: WelcomePageViewModel

    private lateinit var viewEvents: Flow<WelcomePageView.State>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = WelcomePageViewModel(
            appInstallStore = appInstallStore,
            context = mock(),
            pixel = pixel,
            defaultRoleBrowserDialog = defaultRoleBrowserDialog
        )

        viewEvents = events.asFlow().flatMapLatest { viewModel.reduce(it) }
    }

    @After
    fun teardown() {
        events.close()
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldNotShowDialogThenFireAndFinish() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
        }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowDialogAndShowThenFireAndEmitShowDialog() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        val intent = Intent()
        whenever(defaultRoleBrowserDialog.createIntent(any())).thenReturn(intent)

        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.ShowDefaultBrowserDialog(intent))
        }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowDialogNullIntentThenFireAndFinish() = runTest {
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(defaultRoleBrowserDialog.createIntent(any())).thenReturn(null)

        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(pixel).fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
        }
    }

    @Test
    fun whenOnDefaultBrowserSetThenCallDialogShownFireAndFinish() = runTest {
        events.send(WelcomePageView.Event.OnDefaultBrowserSet)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(defaultRoleBrowserDialog).dialogShown()
            verify(pixel).fire(
                AppPixelName.DEFAULT_BROWSER_SET,
                mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
            )
        }
    }

    @Test
    fun whenOnDefaultBrowserNotSetThenCallDialogShownFireAndFinish() = runTest {
        events.send(WelcomePageView.Event.OnDefaultBrowserNotSet)

        viewEvents.test {
            assertTrue(awaitItem() == WelcomePageView.State.Finish)
            verify(defaultRoleBrowserDialog).dialogShown()
            verify(pixel).fire(
                AppPixelName.DEFAULT_BROWSER_NOT_SET,
                mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
            )
        }
    }
}
