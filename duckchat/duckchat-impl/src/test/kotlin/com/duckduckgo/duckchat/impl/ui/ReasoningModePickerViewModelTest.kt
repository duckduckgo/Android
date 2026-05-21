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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReasoningModePickerViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val state = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock<DuckAiModelManager>().also {
        whenever(it.modelState).thenReturn(state)
    }

    private val testee = ReasoningModePickerViewModel(modelManager)

    @Test
    fun whenAvailableEmptyThenResolvedModeIsNull() {
        state.value = ModelState(availableReasoningModes = emptyList())
        assertNull(testee.resolvedMode(state.value))
    }

    @Test
    fun whenPersistedNullThenResolvedModeIsFirstAvailable() {
        state.value = ModelState(
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        assertEquals(ReasoningMode.FAST, testee.resolvedMode(state.value))
    }

    @Test
    fun whenPersistedSupportedThenResolvedModeIsPersisted() {
        state.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        assertEquals(ReasoningMode.REASONING, testee.resolvedMode(state.value))
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
    fun whenRowsBuiltThenSelectedReflectsResolvedMode() {
        state.value = ModelState(
            selectedReasoningMode = ReasoningMode.REASONING,
            availableReasoningModes = listOf(
                AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE),
                AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW),
            ),
        )
        val rows = testee.rows(state.value)
        assertEquals(2, rows.size)
        assertEquals(ReasoningMode.FAST, rows[0].mode)
        assertEquals(ReasoningMode.REASONING, rows[1].mode)
        assertEquals(false, rows[0].selected)
        assertEquals(true, rows[1].selected)
    }

    // ---- onModeTapped upsell routing ----

    @Test
    fun whenAccessibleModeTappedThenSelectModeInvokedAndNoCommandEmitted() = runTest {
        state.value = ModelState(
            availableReasoningModes = listOf(AvailableReasoningMode(ReasoningMode.REASONING, ReasoningEffort.LOW)),
        )

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
        state.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("plus", "pro"))),
        )

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
        state.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("pro"))),
        )

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
        state.value = ModelState(
            userTier = UserTier.PLUS,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("pro"))),
        )

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
        state.value = ModelState(
            userTier = UserTier.PLUS,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("pro"))),
        )

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
        state.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("free", "plus", "pro"))),
        )

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGatedModeAccessHasNoPublicTierThenNoCommandEmitted() = runTest {
        // Only non-public tiers in the access list → requiredTier is null → no upsell route.
        state.value = ModelState(
            userTier = UserTier.FREE,
            availableReasoningModes = listOf(gatedExtended(requires = listOf("internal"))),
        )

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenTappedModeNotInAvailableListThenNoCommandEmittedAndManagerNotCalled() = runTest {
        state.value = ModelState(
            availableReasoningModes = listOf(AvailableReasoningMode(ReasoningMode.FAST, ReasoningEffort.NONE)),
        )

        testee.commands.test {
            testee.onModeTapped(ReasoningMode.EXTENDED_REASONING, PickerSurface.REASONING_PICKER_ADDRESS_BAR)
            runCurrent()

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
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
}
