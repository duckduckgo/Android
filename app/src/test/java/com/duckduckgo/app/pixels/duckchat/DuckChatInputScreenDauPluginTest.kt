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

package com.duckduckgo.app.pixels.duckchat

import com.duckduckgo.app.browser.newaddressbaroption.NewAddressBarPickerDataStore
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName.DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatInputScreenDauPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val pickerDataStore: NewAddressBarPickerDataStore = mock()
    private val userStageStore: UserStageStore = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val pixel: Pixel = mock()

    private val testee = DuckChatInputScreenDauPlugin(
        duckChat = duckChat,
        newAddressBarPickerDataStore = pickerDataStore,
        userStageStore = userStageStore,
        appBuildConfig = appBuildConfig,
        pixel = pixel,
        coroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun setUp() = runTest {
        whenever(duckChat.isInputScreenEverEnabled()).thenReturn(false)
        whenever(pickerDataStore.wasShown()).thenReturn(false)
        whenever(pickerDataStore.displayCount()).thenReturn(0)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
    }

    @Test
    fun `when new DAU day and toggle never enabled then fires pixel with never asked params`() = runTest {
        refreshToNewDay()

        verify(pixel).fire(
            DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED,
            parameters = mapOf(
                "picker_was_shown" to "false",
                "picker_display_count" to "0",
                "is_reinstall" to "false",
                "app_stage" to "established",
            ),
        )
    }

    @Test
    fun `when picker was confirmed on first display then reports shown with count 1`() = runTest {
        whenever(pickerDataStore.wasShown()).thenReturn(true)
        whenever(pickerDataStore.displayCount()).thenReturn(1)

        refreshToNewDay()

        verify(pixel).fire(
            DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED,
            parameters = paramsWith("picker_was_shown" to "true", "picker_display_count" to "1"),
        )
    }

    @Test
    fun `when picker was displayed more than twice then buckets the count as 2_plus`() = runTest {
        whenever(pickerDataStore.wasShown()).thenReturn(true)
        whenever(pickerDataStore.displayCount()).thenReturn(5)

        refreshToNewDay()

        verify(pixel).fire(
            DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED,
            parameters = paramsWith("picker_was_shown" to "true", "picker_display_count" to "2_plus"),
        )
    }

    @Test
    fun `when reinstall user still onboarding then reports reinstall and stage`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)

        refreshToNewDay()

        verify(pixel).fire(
            DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED,
            parameters = paramsWith("is_reinstall" to "true", "app_stage" to "dax_onboarding"),
        )
    }

    @Test
    fun `when new DAU day and toggle was ever enabled then does not fire pixel`() = runTest {
        whenever(duckChat.isInputScreenEverEnabled()).thenReturn(true)

        refreshToNewDay()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun `when same DAU day (oldAtb equals newAtb) then does not fire pixel`() = runTest {
        testee.onDuckAiRetentionAtbRefreshed(oldAtb = "v100-1", newAtb = "v100-1", metadata = emptyMap())
        advanceUntilIdle()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun `when same DAU day but different cohort suffix then does not fire pixel`() = runTest {
        testee.onDuckAiRetentionAtbRefreshed(oldAtb = "v100-1ma", newAtb = "v100-1", metadata = emptyMap())
        advanceUntilIdle()

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    private fun TestScope.refreshToNewDay() {
        testee.onDuckAiRetentionAtbRefreshed(oldAtb = "v100-1", newAtb = "v100-2", metadata = emptyMap())
        advanceUntilIdle()
    }

    private fun paramsWith(vararg overrides: Pair<String, String>): Map<String, String> = mapOf(
        "picker_was_shown" to "false",
        "picker_display_count" to "0",
        "is_reinstall" to "false",
        "app_stage" to "established",
    ) + overrides
}
