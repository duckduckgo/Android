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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UsageLimitFooterPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val snapshot = MutableStateFlow<UsageLimitsSnapshot?>(null)
    private val dismissal = MutableStateFlow<UsageNoticeDismissal?>(null)
    private val repository: DuckAiUsageLimitsRepository = mock()
    private val dismissalStore: UsageNoticeDismissalStore = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val hostContext = MutableStateFlow(duckAiContext())

    private lateinit var testee: UsageLimitFooterPlugin

    @Before
    fun setUp() {
        whenever(repository.usageLimits(BrowserMode.REGULAR)).thenReturn(snapshot)
        whenever(dismissalStore.dismissal).thenReturn(dismissal)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW)
        feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))
        testee = UsageLimitFooterPlugin(
            repository = repository,
            dismissalStore = dismissalStore,
            messageMapper = UsageLimitFooterMessageMapper(),
            duckChatFeature = feature,
            currentTimeProvider = currentTimeProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenPriorityIsReadThenItOutranksTheHighUsageNotice() {
        assertTrue(testee.priority < 100)
    }

    @Test
    fun whenSnapshotArrivesThenFooterBecomesVisibleAndRendersIt() = runTest {
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)

            snapshot.value = approaching(75)

            val state = awaitItem()
            assertTrue(state.visible)
            assertFalse(state.blocksComposer)
            assertEquals("75% of weekly limit", footer.view.findViewById<DaxTextView>(R.id.usageLimitFooterTitle).text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLimitIsReachedAndInputIsFocusedThenComposerIsBlocked() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            val state = awaitItem()
            assertTrue(state.visible)
            assertTrue(state.blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLimitIsReachedAndInputIsUnfocusedThenFooterHidesAndComposerIsFree() = runTest {
        snapshot.value = reached()
        hostContext.value = duckAiContext(isInputFocused = false)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            val state = awaitItem()
            assertFalse(state.visible)
            assertFalse(state.blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenApproachingAndInputLosesFocusThenFooterHides() = runTest {
        snapshot.value = approaching(75)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(isInputFocused = false)

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSnapshotClearsThenFooterHidesAndUnlocks() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().blocksComposer)

            snapshot.value = null

            val state = awaitItem()
            assertFalse(state.visible)
            assertFalse(state.blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDismissedThenStoreIsUpdatedAndNoticeHidesUntilNextBand() = runTest {
        snapshot.value = approaching(55)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            footer.view.findViewById<ImageView>(R.id.usageLimitFooterDismiss).performClick()
            verify(dismissalStore).dismiss(approaching(55)!!.notice)
            dismissal.value = UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, RESETS_AT, 50)

            assertFalse(awaitItem().visible)

            snapshot.value = approaching(76)

            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchIsSelectedOrEditingThenFooterHides() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext().copy(isDuckAiSelected = false)
            assertFalse(awaitItem().visible)

            hostContext.value = duckAiContext(isEditing = true)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFireModeThenRepositoryIsNeverRead() = runTest {
        hostContext.value = duckAiContext(isFireMode = true)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }

        verify(repository, never()).usageLimits(any())
    }

    @Test
    fun whenFlagIsDisabledWhileLimitIsReachedThenComposerIsReleased() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().blocksComposer)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = false))

            val state = awaitItem()
            assertFalse(state.visible)
            assertFalse(state.blocksComposer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFlagIsDisabledThenFooterHidesAndReturnsWhenEnabled() = runTest {
        snapshot.value = approaching(75)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = false))
            assertFalse(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))
            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun approaching(percent: Int) = UsageLimitsSnapshot(
        notice = UsageNotice(
            id = UsageNoticeId.APPROACHING,
            window = UsageWindow.WEEKLY,
            percentUsed = percent,
            resetsAtMillis = RESETS_AT,
            reached = false,
            dismissible = true,
        ),
        cta = null,
    )

    private fun reached() = UsageLimitsSnapshot(
        notice = UsageNotice(
            id = UsageNoticeId.DAILY_REACHED,
            window = UsageWindow.DAILY,
            percentUsed = 100,
            resetsAtMillis = RESETS_AT,
            reached = true,
            dismissible = false,
        ),
        cta = null,
    )

    private fun duckAiContext(
        isEditing: Boolean = false,
        isFireMode: Boolean = false,
        isInputFocused: Boolean = true,
    ) = NativeInputFooterContext(
        isDuckAiSelected = true,
        isEditing = isEditing,
        isFireMode = isFireMode,
        isInputFocused = isInputFocused,
    )

    private companion object {
        const val NOW = 1787659200000L
        const val RESETS_AT = 1788134400000L
    }
}
