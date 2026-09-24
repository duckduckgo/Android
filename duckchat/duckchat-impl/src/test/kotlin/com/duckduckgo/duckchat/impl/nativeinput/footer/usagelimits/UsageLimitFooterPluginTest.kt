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
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.footer.FakeNativeInputFooterHost
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
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
    private val dismissals = MutableStateFlow<Map<UsageWindow, UsageNoticeDismissal>>(emptyMap())
    private val actedOn = MutableStateFlow<UsageNoticeActedOn?>(null)
    private val modelState = MutableStateFlow(ModelState(models = listOf(SONNET, HAIKU), selectedModelId = SONNET.id, isSubscriptionEligible = true))
    private val repository: DuckAiUsageLimitsRepository = mock()
    private val dismissalStore: UsageNoticeDismissalStore = mock()
    private val modelManager: DuckAiModelManager = mock()
    private val subscriptions: Subscriptions = mock()
    private val settingsDao: DuckAiBridgeSettingsDao = mock()
    private val storage: DuckAiBridgeStorage = mock()
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage> = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val hostContext = MutableStateFlow(duckAiContext())
    private val host = FakeNativeInputFooterHost()

    private lateinit var testee: UsageLimitFooterPlugin

    @Before
    fun setUp() {
        whenever(repository.usageLimits(BrowserMode.REGULAR)).thenReturn(snapshot)
        whenever(dismissalStore.dismissals).thenReturn(dismissals)
        whenever(dismissalStore.actedOn).thenReturn(actedOn)
        whenever(modelManager.modelState).thenReturn(modelState)
        whenever(storage.settings).thenReturn(settingsDao)
        whenever(storageProvider.forMode(BrowserMode.REGULAR)).thenReturn(storage)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW)
        feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))
        testee = UsageLimitFooterPlugin(
            repository = repository,
            dismissalStore = dismissalStore,
            messageMapper = UsageLimitFooterMessageMapper(),
            ctaResolver = UsageLimitCtaResolver(),
            modelManager = modelManager,
            subscriptions = subscriptions,
            storageProvider = storageProvider,
            duckChatFeature = feature,
            currentTimeProvider = currentTimeProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenPriorityIsReadThenItOutranksTheHighUsageNotice() {
        assertTrue(testee.priority < 100)
    }

    @Test
    fun whenSnapshotArrivesThenFooterBecomesVisibleAndRendersIt() = runTest {
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)

            footer.view.findViewById<ImageView>(R.id.usageLimitFooterDismiss).performClick()
            verify(dismissalStore).dismiss(approaching(55)!!.notice)
            dismissals.value = mapOf(UsageWindow.WEEKLY to UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, RESETS_AT, 50))

            assertFalse(awaitItem().visible)

            snapshot.value = approaching(76)

            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSearchIsSelectedOrEditingThenFooterHides() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }

        verify(repository, never()).usageLimits(any())
    }

    @Test
    fun whenFlagIsDisabledWhileLimitIsReachedThenComposerIsReleased() = runTest {
        snapshot.value = reached()
        val footer = testee.createFooter(context, hostContext, host)

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
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = false))
            assertFalse(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))
            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCtaHasAnAccessibleCandidateThenSwitchButtonIsShownAndSwitchesOnTap() = runTest {
        snapshot.value = approaching(75).copy(cta = switchCta(HAIKU.id))
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)
            val button = footer.view.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta)
            assertEquals("Switch Model", button.text.toString())

            button.performClick()

            assertEquals(listOf(HAIKU.id), host.selectedModelIds)
            verify(dismissalStore).markActedOn(snapshot.value!!.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCtaHasNoUsableCandidateThenNoticeShowsWithoutButton() = runTest {
        snapshot.value = approaching(75).copy(cta = switchCta("unknown-model"))
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)
            assertEquals(android.view.View.GONE, footer.view.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta).visibility)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSnapshotWasActedOnThenCardStaysHiddenUntilSnapshotChanges() = runTest {
        val acted = approaching(75).copy(cta = switchCta(HAIKU.id))
        snapshot.value = acted
        actedOn.value = UsageNoticeActedOn.of(acted.notice)
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertFalse(awaitItem().visible)

            snapshot.value = approaching(90).copy(cta = switchCta(HAIKU.id))

            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenWeeklyHandoffIsTappedThenEntriesAreWrittenAndPageIsToldAndSnapshotIsActedOn() = runTest {
        val entries = listOf(UsageCtaPutEntry("duckai.fixedCostWindowBypassResetAtById", """{"day":"x"}"""))
        snapshot.value = reached().copy(cta = UsageCta(UsageCtaId.BYPASS_WEEKLY, null, emptyList(), emptyMap(), entries))
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)
            val button = footer.view.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta)
            assertEquals("Start using weekly limit", button.text.toString())

            button.performClick()

            verify(settingsDao).upsert(DuckAiBridgeSettingEntity(key = entries[0].key, value = entries[0].value))
            assertEquals(1, host.startUsingWeeklyLimitCalls)
            verify(dismissalStore).markActedOn(snapshot.value!!.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSubscribeIsTappedThenPurchaseOpensAndCardIsNotActedOn() = runTest {
        whenever(subscriptions.isFreeTrialEligible()).thenReturn(true)
        val subscribeCta = UsageCta(UsageCtaId.SUBSCRIBE, null, emptyList(), emptyMap(), emptyList())
        snapshot.value = reached().copy(id = UsageNoticeId.FREE_REACHED).copy(cta = subscribeCta)
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)
            val button = footer.view.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta)
            assertEquals("Try for Free", button.text.toString())

            button.performClick()

            assertEquals(listOf(UsageLimitFooterPlugin.USAGE_LIMIT_PURCHASE_ORIGIN), host.purchaseOrigins)
            verify(dismissalStore, never()).markActedOn(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserPicksAnOfferedModelFromThePickerThenSnapshotIsActedOn() = runTest {
        snapshot.value = approaching(75).copy(cta = switchCta(HAIKU.id))
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)

            modelState.value = modelState.value.copy(selectedModelId = HAIKU.id)
            hostContext.value = duckAiContext(isInputFocused = false)
            hostContext.value = duckAiContext(isInputFocused = true)

            verify(dismissalStore).markActedOn(snapshot.value!!.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNextBandArrivesAfterActingOnTheCtaThenCardReturns() = runTest {
        val fifty = approaching(50).copy(cta = switchCta(HAIKU.id))
        snapshot.value = fifty
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertTrue(awaitItem().visible)

            // Tap the CTA: the store records the snapshot and the model manager moves to the offered model.
            footer.view.findViewById<DaxButtonSecondary>(R.id.usageLimitFooterCta).performClick()
            actedOn.value = UsageNoticeActedOn.of(fifty.notice)
            assertFalse(awaitItem().visible)
            modelState.value = modelState.value.copy(selectedModelId = HAIKU.id)

            snapshot.value = approaching(75).copy(cta = switchCta(HAIKU.id))

            assertTrue(awaitItem().visible)
            verify(dismissalStore, never()).markActedOn(snapshot.value!!.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPageRepublishesWithADifferentCtaForTheSameNoticeThenCardStaysHidden() = runTest {
        val acted = approaching(75).copy(cta = switchCta(HAIKU.id))
        snapshot.value = acted
        actedOn.value = UsageNoticeActedOn.of(acted.notice)
        val footer = testee.createFooter(context, hostContext, host)

        footer.state.test {
            assertFalse(awaitItem().visible)

            snapshot.value = acted.copy(cta = null)
            expectNoEvents()

            snapshot.value = approaching(90).copy(cta = null)
            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun switchCta(vararg modelIds: String) =
        UsageCta(UsageCtaId.SWITCH_TO_CHEAPER, modelId = null, modelIds = modelIds.toList(), byModelId = emptyMap(), putEntries = emptyList())

    private fun UsageLimitsSnapshot.copy(id: UsageNoticeId) = copy(notice = notice.copy(id = id))

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
        val SONNET =
            AIChatModel(
                id = "claude-sonnet-4-6",
                name = "Sonnet",
                displayName = "Sonnet",
                shortName = "Sonnet",
                accessTier = listOf("free"),
                isAccessible = true,
            )
        val HAIKU =
            AIChatModel(
                id = "claude-haiku-4-5",
                name = "Haiku",
                displayName = "Haiku",
                shortName = "Haiku",
                accessTier = listOf("free"),
                isAccessible = true,
            )
    }
}
