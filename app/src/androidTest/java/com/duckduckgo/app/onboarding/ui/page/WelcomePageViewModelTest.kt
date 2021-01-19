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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.DefaultRoleBrowserDialogExperiment
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

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
    private lateinit var defaultRoleBrowserDialogExperiment: DefaultRoleBrowserDialogExperiment

    private val events = ConflatedBroadcastChannel<WelcomePageView.Event>()

    lateinit var viewModel: WelcomePageViewModel

    private lateinit var viewEvents: Flow<WelcomePageView.State>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        viewModel = WelcomePageViewModel(
            appInstallStore,
            InstrumentationRegistry.getInstrumentation().targetContext,
            pixel,
            defaultRoleBrowserDialogExperiment
        )

        viewEvents = events.asFlow().flatMapLatest { viewModel.reduce(it) }
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldNotShowXpThenFinish() = coroutineRule.runBlocking {
        whenever(defaultRoleBrowserDialogExperiment.shouldShowExperiment())
            .thenReturn(false)

        val launch = launch {
            viewEvents.collect { state ->
                assertTrue(state == WelcomePageView.State.Finish)
            }
        }
        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        launch.cancel()
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowXpAndShowThenEmitShowDialog() = coroutineRule.runBlocking {
        whenever(defaultRoleBrowserDialogExperiment.shouldShowExperiment())
            .thenReturn(true)
        val intent = Intent()
        whenever(defaultRoleBrowserDialogExperiment.createIntent(any()))
            .thenReturn(intent)

        val launch = launch {
            viewEvents.collect { state ->
                assertTrue(state == WelcomePageView.State.ShowDefaultBrowserDialog(intent))
            }
        }
        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        launch.cancel()
    }

    @Test
    fun whenOnPrimaryCtaClickedAndShouldShowXpNullIntentThenFireAndFinish() = coroutineRule.runBlocking {
        whenever(defaultRoleBrowserDialogExperiment.shouldShowExperiment())
            .thenReturn(true)
        whenever(defaultRoleBrowserDialogExperiment.createIntent(any()))
            .thenReturn(null)

        val launch = launch {
            viewEvents.collect { state ->
                assertTrue(state == WelcomePageView.State.Finish)
            }
        }
        events.send(WelcomePageView.Event.OnPrimaryCtaClicked)

        verify(pixel).fire(Pixel.PixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)

        launch.cancel()
    }

    @Test
    fun whenOnDefaultBrowserSetThenCallExperimentShownFireAndFinish() = coroutineRule.runBlocking {
        val launch = launch {
            viewEvents.collect { state ->
                assertTrue(state == WelcomePageView.State.Finish)
            }
        }
        events.send(WelcomePageView.Event.OnDefaultBrowserSet)

        verify(defaultRoleBrowserDialogExperiment).experimentShown()
        verify(pixel).fire(
            Pixel.PixelName.DEFAULT_BROWSER_SET,
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
        )

        launch.cancel()
    }

    @Test
    fun whenOnDefaultBrowserNotSetThenCallExperimentShownFireAndFinish() = coroutineRule.runBlocking {
        val launch = launch {
            viewEvents.collect { state ->
                assertTrue(state == WelcomePageView.State.Finish)
            }
        }
        events.send(WelcomePageView.Event.OnDefaultBrowserNotSet)

        verify(defaultRoleBrowserDialogExperiment).experimentShown()
        verify(pixel).fire(
            Pixel.PixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
        )

        launch.cancel()
    }
}
