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

package com.duckduckgo.duckchat.impl.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetAvailabilityTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val packageManager: PackageManager = mock()
    private val appWidgetManager: AppWidgetManager = mock()
    private val pixel: Pixel = mock()
    private val homeScreenWidgetLifecycle: HomeScreenWidgetLifecycle = mock()
    private val context: Context = mock<Context>().also {
        whenever(it.packageName).thenReturn("com.duckduckgo.mobile.android")
        whenever(it.packageManager).thenReturn(packageManager)
        whenever(it.getSystemService(AppWidgetManager::class.java)).thenReturn(appWidgetManager)
    }
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val userEnabledFlow = MutableStateFlow(true)
    private val repository: DuckChatFeatureRepository = mock<DuckChatFeatureRepository>().also {
        whenever(it.observeDuckChatUserEnabled()).thenReturn(userEnabledFlow)
        runBlocking { whenever(it.isDuckChatUserEnabled()).thenReturn(true) }
    }
    private val receiver = ComponentName("com.duckduckgo.mobile.android", DuckAiWidgetProvider::class.java.name)

    private val testee = DuckAiWidgetAvailability(
        context = context,
        duckChatFeature = duckChatFeature,
        duckChatFeatureRepository = repository,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        pixel = pixel,
        homeScreenWidgetLifecycle = homeScreenWidgetLifecycle,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenAllConditionsMetThenEnablesReceiver() = runTest {
        givenConditions()
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenFeatureOffThenDisablesReceiver() = runTest {
        givenConditions(feature = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenUserSettingOffThenDisablesReceiver() = runTest {
        givenConditions(user = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenDisablingReceiverWithPlacedWidgetsThenFiresDeletedPixel() = runTest {
        givenConditions(feature = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        whenever(appWidgetManager.getAppWidgetIds(receiver)).thenReturn(intArrayOf(7))

        testee.onPrivacyConfigDownloaded()

        verify(pixel).fire(DuckChatPixelName.DUCK_AI_WIDGET_DELETED)
        verify(homeScreenWidgetLifecycle).onWidgetDisabled()
    }

    @Test
    fun whenDisablingReceiverWithoutPlacedWidgetsThenDoesNotFireDeletedPixel() = runTest {
        givenConditions(feature = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        whenever(appWidgetManager.getAppWidgetIds(receiver)).thenReturn(intArrayOf())

        testee.onPrivacyConfigDownloaded()

        verify(pixel, never()).fire(eq(DuckChatPixelName.DUCK_AI_WIDGET_DELETED), any(), any(), any())
        verify(homeScreenWidgetLifecycle, never()).onWidgetDisabled()
    }

    @Test
    fun whenReceiverAlreadyDisabledThenDoesNotFireDeletedPixelAgain() = runTest {
        givenConditions(feature = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        whenever(appWidgetManager.getAppWidgetIds(receiver)).thenReturn(intArrayOf(7))

        testee.onPrivacyConfigDownloaded()

        verify(pixel, never()).fire(eq(DuckChatPixelName.DUCK_AI_WIDGET_DELETED), any(), any(), any())
        verify(homeScreenWidgetLifecycle, never()).onWidgetDisabled()
    }

    @Test
    fun whenUserSettingChangesAfterStartThenReceiverIsDisabledThenEnabled() = runTest {
        givenConditions()
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        testee.onCreate(mock())
        verify(packageManager, never()).setComponentEnabledSetting(any(), any(), any())

        givenConditions(user = false)
        userEnabledFlow.value = false
        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)

        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        givenConditions(user = true)
        userEnabledFlow.value = true
        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenInitialFlowEmissionThenDoesNotDisableReceiver() = runTest {
        givenConditions(user = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

        testee.onCreate(mock())

        verify(packageManager, never()).setComponentEnabledSetting(any(), any(), any())
    }

    @Test
    fun whenToggleOnAndReceiverDefaultThenEnablesReceiverWithoutKillingApp() {
        givenToggle(enabled = true)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenToggleOffAndReceiverEnabledThenDisablesReceiver() {
        givenToggle(enabled = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    @Test
    fun whenToggleOnAndReceiverAlreadyEnabledThenNoChange() {
        givenToggle(enabled = true)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager, never()).setComponentEnabledSetting(any(), any(), any())
    }

    @Test
    fun whenToggleOffAndReceiverDefaultThenNoChange() {
        givenToggle(enabled = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager, never()).setComponentEnabledSetting(any(), any(), any())
    }

    @Test
    fun whenToggleOffAndReceiverAlreadyDisabledThenNoChange() {
        givenToggle(enabled = false)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

        testee.onPrivacyConfigDownloaded()

        verify(packageManager, never()).setComponentEnabledSetting(any(), eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED), any())
    }

    @Test
    fun whenProcessCreatedThenStateIsSynced() {
        givenToggle(enabled = true)
        givenReceiverState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        testee.onCreate(mock())

        verify(packageManager).setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    private suspend fun givenConditions(feature: Boolean = true, user: Boolean = true) {
        givenToggle(enabled = true)
        duckChatFeature.self().setRawStoredState(State(enable = feature))
        whenever(repository.isDuckChatUserEnabled()).thenReturn(user)
    }

    private fun givenToggle(enabled: Boolean) {
        duckChatFeature.duckAiWidget().setRawStoredState(State(enable = enabled))
        duckChatFeature.self().setRawStoredState(State(enable = true))
    }

    private fun givenReceiverState(state: Int) {
        whenever(packageManager.getComponentEnabledSetting(receiver)).thenReturn(state)
    }
}
