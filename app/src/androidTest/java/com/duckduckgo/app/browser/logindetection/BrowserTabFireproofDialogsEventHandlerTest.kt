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

package com.duckduckgo.app.browser.logindetection

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler.Event
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler.Event.FireproofWebSiteSuccess
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventKey.*
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper.LoginDetectorSetting
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class BrowserTabFireproofDialogsEventHandlerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockUserEventsStore: UserEventsStore = mock()
    private val mockPixel: Pixel = mock()
    private val mockAppSettingsPreferencesStore: SettingsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private lateinit var db: AppDatabase
    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao
    private lateinit var testee: FireproofDialogsEventHandler

    @Before
    fun before() {
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()
        val fireproofWebsiteRepository = FireproofWebsiteRepository(fireproofWebsiteDao, coroutineRule.testDispatcherProvider, mock())
        testee = BrowserTabFireproofDialogsEventHandler(
            mockUserEventsStore,
            mockPixel,
            fireproofWebsiteRepository,
            mockAppSettingsPreferencesStore,
            mockVariantManager,
            coroutineRule.testDispatcherProvider
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenFireproofLoginShownBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onFireproofLoginDialogShown()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DIALOG_SHOWN,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenFireproofLoginShownAfterUserTriedFireButtonThenPixelSent() = runTest {
        givenUserTriedFireButton()

        testee.onFireproofLoginDialogShown()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DIALOG_SHOWN,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "true")
        )
    }

    @Test
    fun whenUserConfirmsToFireproofWebsiteBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onUserConfirmedFireproofDialog("twitter.com")

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_WEBSITE_LOGIN_ADDED,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenUserConfirmsToFireproofWebsiteAfterUserTriedFireButtonThenPixelSent() = runTest {
        givenUserTriedFireButton()

        testee.onUserConfirmedFireproofDialog("twitter.com")

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_WEBSITE_LOGIN_ADDED,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "true")
        )
    }

    @Test
    fun whenUserConfirmsToFireproofWebsiteThenEventEmitted() = runTest {
        testee.onUserConfirmedFireproofDialog("twitter.com")

        val event = testee.event.blockingObserve()
        assertTrue(event is FireproofWebSiteSuccess)
    }

    @Test
    fun whenUserConfirmsToFireproofWebsiteThenResetLoginDismissedEvents() = runTest {
        testee.onUserConfirmedFireproofDialog("twitter.com")

        verify(mockUserEventsStore).removeUserEvent(FIREPROOF_LOGIN_DIALOG_DISMISSED)
    }

    @Test
    fun whenUserDismissesFireproofLoginDialogBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onUserDismissedFireproofLoginDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_WEBSITE_LOGIN_DISMISS,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenUserDismissesFireproofLoginDialogAfterUserTriedFireButtonThenPixelSent() = runTest {
        givenUserTriedFireButton()
        testee.onUserDismissedFireproofLoginDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_WEBSITE_LOGIN_DISMISS,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "true")
        )
    }

    @Test
    fun whenUserDismissedFireproofLoginDialogThenRegisterEvent() = runTest {
        testee.onUserDismissedFireproofLoginDialog()

        verify(mockUserEventsStore).registerUserEvent(FIREPROOF_LOGIN_DIALOG_DISMISSED)
    }

    @Test
    fun whenUserDismissedFireproofLoginDialogTwiceInRowThenAskToDisableLoginDetection() = runTest {
        givenUserPreviouslyDismissedDialog()

        testee.onUserDismissedFireproofLoginDialog()

        val event = testee.event.blockingObserve()
        assertTrue(event is Event.AskToDisableLoginDetection)
    }

    @Test
    fun whenUserEnabledFireproofLoginDetectionThenNeverAskToDisableIt() = runTest {
        givenUserEnabledFireproofLoginDetection()
        givenUserPreviouslyDismissedDialog()

        testee.onUserDismissedFireproofLoginDialog()

        val event = testee.event.blockingObserve()
        assertNull(event)
    }

    @Test
    fun whenUserDidNotDisableLoginDetectionThenNeverAskToDisableItAgain() = runTest {
        givenUserDidNotDisableLoginDetection()
        givenUserPreviouslyDismissedDialog()

        testee.onUserDismissedFireproofLoginDialog()

        val event = testee.event.blockingObserve()
        assertNull(event)
    }

    @Test
    fun whenDisableLoginDetectionDialogShownBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onDisableLoginDetectionDialogShown()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_SHOWN,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenUserConfirmsToDisableLoginDetectionBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onUserConfirmedDisableLoginDetectionDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_DISABLE,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenUserConfirmsToDisableLoginDetectionAfterUserTriedFireButtonThenPixelSent() = runTest {
        givenUserTriedFireButton()

        testee.onUserConfirmedDisableLoginDetectionDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_DISABLE,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "true")
        )
    }

    @Test
    fun whenUserConfirmsToDisableLoginDetectionThenLoginDetectionDisabled() = runTest {
        testee.onUserConfirmedDisableLoginDetectionDialog()

        verify(mockAppSettingsPreferencesStore).appLoginDetection = LoginDetectorSetting.NEVER
    }

    @Test
    fun whenUserDismissesDisableFireproofLoginDialogBeforeUserTriedFireButtonThenPixelSent() = runTest {
        testee.onUserDismissedDisableLoginDetectionDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_CANCEL,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "false")
        )
    }

    @Test
    fun whenUserDismissesDisableFireproofLoginDialogAfterUserTriedFireButtonThenPixelSent() = runTest {
        givenUserTriedFireButton()

        testee.onUserDismissedDisableLoginDetectionDialog()

        verify(mockPixel).fire(
            pixel = AppPixelName.FIREPROOF_LOGIN_DISABLE_DIALOG_CANCEL,
            parameters = mapOf(Pixel.PixelParameter.FIRE_EXECUTED to "true")
        )
    }

    @Test
    fun whenUserDismissesDisableFireproofLoginDialogThenRegisterEvent() = runTest {
        givenUserTriedFireButton()

        testee.onUserDismissedDisableLoginDetectionDialog()

        verify(mockUserEventsStore).registerUserEvent(FIREPROOF_DISABLE_DIALOG_DISMISSED)
    }

    private suspend fun givenUserDidNotDisableLoginDetection() {
        whenever(mockUserEventsStore.getUserEvent(FIREPROOF_DISABLE_DIALOG_DISMISSED)).thenReturn(UserEventEntity(FIREPROOF_DISABLE_DIALOG_DISMISSED))
    }

    private suspend fun givenUserEnabledFireproofLoginDetection() {
        whenever(mockUserEventsStore.getUserEvent(USER_ENABLED_FIREPROOF_LOGIN)).thenReturn(UserEventEntity(USER_ENABLED_FIREPROOF_LOGIN))
    }

    private suspend fun givenUserPreviouslyDismissedDialog() {
        whenever(mockUserEventsStore.getUserEvent(FIREPROOF_LOGIN_DIALOG_DISMISSED))
            .thenReturn(UserEventEntity(FIREPROOF_LOGIN_DIALOG_DISMISSED))
    }

    private suspend fun givenUserTriedFireButton() {
        whenever(mockUserEventsStore.getUserEvent(FIRE_BUTTON_EXECUTED)).thenReturn(UserEventEntity(FIRE_BUTTON_EXECUTED))
    }
}
