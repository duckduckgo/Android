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

package com.duckduckgo.duckchat.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.AvailableReasoningMode
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.ReasoningEffort
import com.duckduckgo.duckchat.impl.models.ReasoningEffortAccess
import com.duckduckgo.duckchat.impl.models.ReasoningMode
import com.duckduckgo.duckchat.impl.models.UserTier
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.PickerSurface
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.ReasoningModePickerViewModel
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.UpsellCommand
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReasoningModePickerViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val modelState = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(modelState)
    }

    private val nativeInputState = MutableStateFlow(NativeInputState.zero())
    private val nativeInputStateProvider: NativeInputStateProvider = mock<NativeInputStateProvider>().also {
        whenever(it.state).thenReturn(nativeInputState)
    }
    private val duckAiChatStore: DuckAiChatStore = mock()

    private lateinit var testee: ReasoningModePickerViewModel

    @Before
    fun setUp() {
        testee = ReasoningModePickerViewModel(
            modelManager = modelManager,
            nativeInputStateProvider = nativeInputStateProvider,
            duckAiChatStore = duckAiChatStore,
        )
    }

    @Test
    fun whenAvailableEmptyThenDisplayedModeIsNullAndNotVisible() = runTest {
        modelState.value = ModelState(availableReasoningModes = emptyList())
        runCurrent()
        assertNull(testee.state.value.displayedMode)
        assertFalse(testee.state.value.visible)
    }

    @Test
    fun whenPersistedNullThenDisplayedModeIsFirstAvailable() = runTest {
        modelState.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        runCurrent()
        assertEquals(ReasoningMode.FAST, testee.state.value.displayedMode)
    }

    @Test
    fun whenPersistedSupportedThenDisplayedModeIsPersisted() = runTest {
        modelState.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        runCurrent()
        assertEquals(ReasoningMode.REASONING, testee.state.value.displayedMode)
    }

    @Test
    fun whenIconResForEachModeThenReturnsExpectedDrawable() {
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_fast_24,
            testee.iconResFor(ReasoningMode.FAST),
        )
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_thinking_24,
            testee.iconResFor(ReasoningMode.REASONING),
        )
        assertEquals(
            com.duckduckgo.duckchat.impl.R.drawable.ic_reasoning_extended_24,
            testee.iconResFor(ReasoningMode.EXTENDED_REASONING),
        )
    }

    @Test
    fun whenRowsBuiltThenSelectedReflectsDisplayedMode() = runTest {
        modelState.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        runCurrent()
        val rows = testee.state.value.rows
        assertEquals(2, rows.size)
        assertEquals(ReasoningMode.FAST, rows[0].mode)
        assertEquals(ReasoningMode.REASONING, rows[1].mode)
        assertEquals(false, rows[0].selected)
        assertEquals(true, rows[1].selected)
    }

    // ---- onModeTapped upsell routing ----

    @Test
    fun whenAccessibleModeTappedThenSelectModeInvokedAndNoCommandEmitted() = runTest {
        modelState.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)
            runCurrent()

            verify(modelManager).selectReasoningMode(ReasoningMode.REASONING)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFreeUserTapsGatedModeRequiringPlusFromAddressBarThenLaunchPurchaseEmitted() = runTest {
        modelState.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("plus", "pro")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            assertEquals(
                UpsellCommand.LaunchPurchase(PickerSurface.REASONING_PICKER_ADDRESS_BAR.origin),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFreeUserTapsGatedModeRequiringProFromDuckAiTabThenLaunchPurchaseEmittedWithDuckAiOrigin() = runTest {
        modelState.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("pro")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)

            assertEquals(
                UpsellCommand.LaunchPurchase(PickerSurface.REASONING_PICKER_DUCK_AI_TAB.origin),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPlusUserTapsGatedModeRequiringProFromAddressBarThenLaunchUpgradeEmitted() = runTest {
        modelState.value = ModelState(
            userTier = UserTier.PLUS,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("pro")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            assertEquals(
                UpsellCommand.LaunchUpgrade(PickerSurface.REASONING_PICKER_ADDRESS_BAR.origin),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenPlusUserTapsGatedModeRequiringProFromDuckAiTabThenLaunchUpgradeEmittedWithDuckAiOrigin() = runTest {
        modelState.value = ModelState(
            userTier = UserTier.PLUS,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("pro")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)

            assertEquals(
                UpsellCommand.LaunchUpgrade(PickerSurface.REASONING_PICKER_DUCK_AI_TAB.origin),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGatedModeRequiresFreeTierThenNoCommandEmitted() = runTest {
        // Pathological: a "gated" entry whose access list still includes FREE → no upsell route.
        modelState.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("free", "plus", "pro")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGatedModeAccessHasNoPublicTierThenNoCommandEmitted() = runTest {
        // Only non-public tiers in the access list → requiredTier is null → no upsell route.
        modelState.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                gatedExtended(requires = listOf("internal")),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenTappedModeNotInAvailableListThenNoCommandEmittedAndManagerNotCalled() = runTest {
        modelState.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        runCurrent()

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)
            runCurrent()

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    // ---- chat-aware behaviour ----

    @Test
    fun whenChatIdSetThenRowsAreDerivedFromChatsModel() = runTest {
        // Globally selected model offers only FAST. Chat's model offers FAST + REASONING.
        modelState.value = ModelState(
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
            availableReasoningModes = listOf(AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE)),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "chat-model", lastEdit = "now", pinned = false),
        )

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        val rows = testee.state.value.rows
        assertEquals(2, rows.size)
        assertEquals(ReasoningMode.FAST, rows[0].mode)
        assertEquals(ReasoningMode.REASONING, rows[1].mode)
    }

    @Test
    fun whenChatHasReasoningModeThenDisplayedModeMatchesChat() = runTest {
        modelState.value = ModelState(
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.REASONING.rawValue,
            ),
        )

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        assertEquals(ReasoningMode.REASONING, testee.state.value.displayedMode)
    }

    @Test
    fun whenChatScopedReasoningModeSetThenItOverridesChatStoredMode() = runTest {
        modelState.value = ModelState(
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.FAST.rawValue,
            ),
        )

        modelState.value = modelState.value.copy(chatScopedReasoningMode = ReasoningMode.REASONING)
        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        assertEquals(ReasoningMode.REASONING, testee.state.value.displayedMode)
    }

    @Test
    fun whenChatIdSetButChatLookupInFlightThenPickerHiddenAndTapDoesNotWriteGlobal() = runTest {
        // Global is configured with two accessible modes so the picker would be visible if the
        // resolver fell back to global rows during the load window.
        modelState.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
        )
        val pending = CompletableDeferred<DuckAiChat?>()
        duckAiChatStore.stub {
            onBlocking { getChatById("chat-1") } doSuspendableAnswer { pending.await() }
        }

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        assertFalse(testee.state.value.visible)
        testee.onModeTapped(ReasoningMode.REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)
        runCurrent()

        verify(modelManager, never()).selectReasoningMode(any())
        verify(modelManager, never()).setChatScopedReasoningMode(any())

        pending.complete(
            DuckAiChat(chatId = "chat-1", title = "t", model = "chat-model", lastEdit = "now", pinned = false),
        )
        runCurrent()
        assertTrue(testee.state.value.visible)
    }

    @Test
    fun whenChatIdFlipsDuringLoadingThenPickerHiddenAndTapsDropped() = runTest {
        // Warm transition: currentChat is still chat-A while nativeState.chatId is "chat-B" and the
        // getChatById("chat-B") suspend is in flight. The chatMatches guard hides the picker so a
        // tap can't land in the global chatScopedReasoningMode slot mis-scoped to chat-B.
        modelState.value = ModelState(
            models = listOf(
                aiModel(id = "model-A", supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
                aiModel(id = "model-B", supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-A")).thenReturn(
            DuckAiChat(chatId = "chat-A", title = "t", model = "model-A", lastEdit = "now", pinned = false),
        )
        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-A")
        runCurrent()
        assertTrue(testee.state.value.visible)

        // Flip to chat-B with the lookup suspended.
        val pending = CompletableDeferred<DuckAiChat?>()
        duckAiChatStore.stub {
            onBlocking { getChatById("chat-B") } doSuspendableAnswer { pending.await() }
        }
        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-B")
        runCurrent()

        assertFalse(testee.state.value.visible)
        testee.onModeTapped(ReasoningMode.REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)
        runCurrent()
        verify(modelManager, never()).setChatScopedReasoningMode(any())

        // Lookup resolves to chat-B → picker becomes visible.
        pending.complete(
            DuckAiChat(chatId = "chat-B", title = "t", model = "model-B", lastEdit = "now", pinned = false),
        )
        runCurrent()
        assertTrue(testee.state.value.visible)
    }

    @Test
    fun whenChatLoadedButItsModelNotInModelsListThenPickerHiddenAndTapsDropped() = runTest {
        // Chat references "missing-model" but the models list has only "other-model". Without this
        // guard, the picker would show global rows and a tap would write chat-scoped, but submission
        // (which gates on chatModel != null) would silently ignore it. Hide the picker instead.
        modelState.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
            models = listOf(
                aiModel(id = "other-model", supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW)),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "missing-model", lastEdit = "now", pinned = false),
        )

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        assertFalse(testee.state.value.visible)

        testee.onModeTapped(ReasoningMode.REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)
        runCurrent()

        verify(modelManager, never()).selectReasoningMode(any())
        verify(modelManager, never()).setChatScopedReasoningMode(any())
    }

    @Test
    fun whenInExistingChatAndAccessibleModeTappedThenWritesChatScopedAndDoesNotTouchGlobal() = runTest {
        modelState.value = ModelState(
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(chatId = "chat-1", title = "t", model = "chat-model", lastEdit = "now", pinned = false),
        )

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()

        testee.onModeTapped(ReasoningMode.REASONING, PickerSurface.REASONING_PICKER_DUCK_AI_TAB)
        runCurrent()

        verify(modelManager).setChatScopedReasoningMode(ReasoningMode.REASONING)
        verify(modelManager, never()).selectReasoningMode(any())
    }

    @Test
    fun whenChatIdClearedThenStateFallsBackToGlobal() = runTest {
        modelState.value = ModelState(
            selectedReasoningMode = ReasoningMode.FAST,
            availableReasoningModes = listOf(AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE)),
            models = listOf(
                aiModel(
                    id = "chat-model",
                    supported = listOf(ReasoningEffort.NONE, ReasoningEffort.LOW),
                ),
            ),
        )
        whenever(duckAiChatStore.getChatById("chat-1")).thenReturn(
            DuckAiChat(
                chatId = "chat-1",
                title = "t",
                model = "chat-model",
                lastEdit = "now",
                pinned = false,
                reasoningMode = ReasoningMode.REASONING.rawValue,
            ),
        )

        nativeInputState.value = NativeInputState.zero().copy(chatId = "chat-1")
        runCurrent()
        assertEquals(ReasoningMode.REASONING, testee.state.value.displayedMode)

        nativeInputState.value = NativeInputState.zero().copy(chatId = null)
        runCurrent()
        assertEquals(ReasoningMode.FAST, testee.state.value.displayedMode)
        assertTrue(testee.state.value.rows.isNotEmpty())
    }

    private fun gatedExtended(requires: List<String>) = AvailableReasoningMode(
        mode = ReasoningMode.EXTENDED_REASONING,
        effort = ReasoningEffort.MEDIUM,
        access = ReasoningEffortAccess(
            effort = ReasoningEffort.MEDIUM,
            accessTier = requires,
            isAccessible = false,
        ),
    )

    private fun aiModel(
        id: String,
        supported: List<ReasoningEffort>,
        access: List<ReasoningEffortAccess> = emptyList(),
    ): AIChatModel = AIChatModel(
        id = id,
        name = id,
        displayName = id,
        shortName = id,
        accessTier = listOf("free", "plus", "pro"),
        isAccessible = true,
        supportedReasoningEfforts = supported,
        reasoningEffortAccess = access,
    )
}
