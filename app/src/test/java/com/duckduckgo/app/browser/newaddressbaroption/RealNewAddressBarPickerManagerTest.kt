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

package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNewAddressBarPickerManagerTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val duckAiFeatureStateMock: DuckAiFeatureState = mock()
    private val duckChatMock: DuckChat = mock()
    private val userStageStoreMock: UserStageStore = mock()
    private val dataStoreMock: NewAddressBarPickerDataStore = mock()
    private val dialogFactoryMock: NewAddressBarPickerBottomSheetDialogFactory = mock()
    private val pixelMock: Pixel = mock()
    private val appThemeMock: AppTheme = mock()
    private val dialogMock: BottomSheetDialog = mock()

    private val showPickerFlow = MutableStateFlow(false)

    private lateinit var testee: RealNewAddressBarPickerManager

    @Before
    fun setUp() =
        runTest {
            whenever(duckAiFeatureStateMock.showAIChatAddressBarOptionChoiceScreen).thenReturn(showPickerFlow)
            whenever(dialogFactoryMock.create(any(), any(), any())).thenReturn(dialogMock)
            whenever(appThemeMock.isLightModeEnabled()).thenReturn(true)
            whenever(dataStoreMock.incrementDisplayCount()).thenReturn(1)

            testee =
                RealNewAddressBarPickerManager(
                    duckAiFeatureStateMock,
                    duckChatMock,
                    userStageStoreMock,
                    dataStoreMock,
                    dialogFactoryMock,
                    pixelMock,
                    appThemeMock,
                    coroutineTestRule.testScope,
                    coroutineTestRule.testDispatcherProvider,
                )
        }

    @Test
    fun `when all conditions met then shows dialog`() =
        runTest {
            setupAllConditionsMet()

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock).create(any(), any(), any())
            verify(dialogMock).show()
        }

    @Test
    fun `when picker flag disabled then does not show`() =
        runTest {
            setupAllConditionsMet()
            showPickerFlow.value = false

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock, never()).create(any(), any(), any())
            verify(dataStoreMock, never()).setAsShown()
        }

    @Test
    fun `when duck ai disabled then does not show`() =
        runTest {
            setupAllConditionsMet()
            whenever(duckChatMock.isEnabled()).thenReturn(false)

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when onboarding is not completed then does not show`() =
        runTest {
            setupAllConditionsMet()
            whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.NEW)

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock, never()).create(any(), any(), any())
            verify(dataStoreMock, never()).setAsShown()
        }

    @Test
    fun `when input screen was ever enabled then does not show`() =
        runTest {
            setupAllConditionsMet()
            whenever(duckChatMock.isInputScreenEverEnabled()).thenReturn(true)

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when already shown then does not show`() =
        runTest {
            setupAllConditionsMet()
            whenever(dataStoreMock.wasShown()).thenReturn(true)

            testee.showChoiceScreen(mock())

            verify(dialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when activity is finishing then does not show`() =
        runTest {
            setupAllConditionsMet()
            val activity = mock<DuckDuckGoActivity>()
            whenever(activity.isFinishing).thenReturn(true)

            testee.showChoiceScreen(activity)

            verify(dialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when activity is destroyed then does not show`() =
        runTest {
            setupAllConditionsMet()
            val activity = mock<DuckDuckGoActivity>()
            whenever(activity.isDestroyed).thenReturn(true)

            testee.showChoiceScreen(activity)

            verify(dialogFactoryMock, never()).create(any(), any(), any())
        }

    @Test
    fun `when dialog displayed then fires displayed pixel`() =
        runTest {
            setupAllConditionsMet()
            var capturedCallback: NewAddressBarCallback? = null
            whenever(dialogFactoryMock.create(any(), any(), any())).thenAnswer {
                capturedCallback = it.getArgument<NewAddressBarCallback?>(2)
                dialogMock
            }

            testee.showChoiceScreen(mock())

            assertNotNull(capturedCallback)
            capturedCallback!!.onDisplayed()
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_COUNT)
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_DISPLAYED_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when confirmed with search and ai then enables toggle and fires confirmed pixel`() =
        runTest {
            setupAllConditionsMet()
            var capturedCallback: NewAddressBarCallback? = null
            whenever(dialogFactoryMock.create(any(), any(), any())).thenAnswer {
                capturedCallback = it.getArgument<NewAddressBarCallback?>(2)
                dialogMock
            }

            testee.showChoiceScreen(mock())

            assertNotNull(capturedCallback)
            capturedCallback!!.onConfirmed(true)
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(duckChatMock).setInputScreenUserSetting(true)
            verify(duckChatMock).onAddressBarPickerDuckAiSelected()
            verify(dataStoreMock).setAsShown()
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_COUNT, mapOf("selection" to "search_and_ai"))
            verify(pixelMock).fire(
                AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_DAILY,
                mapOf("selection" to "search_and_ai"),
                type = Pixel.PixelType.Daily(),
            )
        }

    @Test
    fun `when confirmed with search only then does not enable toggle and fires confirmed pixel`() =
        runTest {
            setupAllConditionsMet()
            var capturedCallback: NewAddressBarCallback? = null
            whenever(dialogFactoryMock.create(any(), any(), any())).thenAnswer {
                capturedCallback = it.getArgument<NewAddressBarCallback?>(2)
                dialogMock
            }

            testee.showChoiceScreen(mock())

            assertNotNull(capturedCallback)
            capturedCallback!!.onConfirmed(false)
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(duckChatMock, never()).setInputScreenUserSetting(any())
            verify(duckChatMock, never()).onAddressBarPickerDuckAiSelected()
            verify(dataStoreMock).setAsShown()
            verify(pixelMock).fire(AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_COUNT, mapOf("selection" to "search_only"))
            verify(pixelMock).fire(
                AppPixelName.NEW_ADDRESS_BAR_PICKER_V2_CONFIRMED_DAILY,
                mapOf("selection" to "search_only"),
                type = Pixel.PixelType.Daily(),
            )
        }

    @Test
    fun `when displayed reaches max count then marks as shown`() =
        runTest {
            setupAllConditionsMet()
            whenever(dataStoreMock.incrementDisplayCount()).thenReturn(2)
            var capturedCallback: NewAddressBarCallback? = null
            whenever(dialogFactoryMock.create(any(), any(), any())).thenAnswer {
                capturedCallback = it.getArgument<NewAddressBarCallback?>(2)
                dialogMock
            }

            testee.showChoiceScreen(mock())

            assertNotNull(capturedCallback)
            capturedCallback!!.onDisplayed()
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(dataStoreMock).setAsShown()
        }

    @Test
    fun `when displayed below max count then does not mark as shown`() =
        runTest {
            setupAllConditionsMet()
            whenever(dataStoreMock.incrementDisplayCount()).thenReturn(1)
            var capturedCallback: NewAddressBarCallback? = null
            whenever(dialogFactoryMock.create(any(), any(), any())).thenAnswer {
                capturedCallback = it.getArgument<NewAddressBarCallback?>(2)
                dialogMock
            }

            testee.showChoiceScreen(mock())

            assertNotNull(capturedCallback)
            capturedCallback!!.onDisplayed()
            coroutineTestRule.testScope.advanceUntilIdle()

            verify(dataStoreMock, never()).setAsShown()
        }

    private suspend fun setupAllConditionsMet() {
        showPickerFlow.value = true
        whenever(duckChatMock.isEnabled()).thenReturn(true)
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(duckChatMock.isInputScreenEverEnabled()).thenReturn(false)
        whenever(dataStoreMock.wasShown()).thenReturn(false)
    }
}
