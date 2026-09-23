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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HighUsageModelFooterPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
    )
    private val modelState = MutableStateFlow(ModelState())
    private val modelManager: DuckAiModelManager = mock()
    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val hostContext = MutableStateFlow(duckAiContext())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: HighUsageModelFooterPlugin

    @Before
    fun setUp() {
        whenever(modelManager.modelState).thenReturn(modelState)
        feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("high_usage_footer_${UUID.randomUUID()}") },
        )
        testee = plugin(HighUsageModelNoticeDismissalStore(dataStore))
    }

    @Test
    fun whenSelectedModelChangesThenFooterVisibilityUpdates() = runTest {
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)

            modelState.value = highUsageModel()

            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenHostContextChangesThenFooterVisibilityUpdates() = runTest {
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(isEditing = true)

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInputFocusChangesThenFooterVisibilityUpdates() = runTest {
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            hostContext.value = duckAiContext(isInputFocused = false)

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUsageWarningsFeatureIsDisabledThenVisibleFooterHidesWithoutOtherStateChanging() = runTest {
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = false))

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUsageWarningsFeatureIsEnabledThenHiddenFooterShowsWithoutOtherStateChanging() = runTest {
        feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = false))
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)

            feature.duckAiUsageWarnings().setRawStoredState(Toggle.State(enable = true))

            assertTrue(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFireModeThenDismissalStorageIsNotObserved() = runTest {
        var subscriptions = 0
        val dismissalStore: HighUsageModelNoticeDismissalStore = mock()
        whenever(dismissalStore.dismissedModelIds).thenReturn(
            flow {
                subscriptions++
                emit(emptySet())
                awaitCancellation()
            },
        )
        val testee = plugin(dismissalStore)
        modelState.value = highUsageModel()
        hostContext.value = duckAiContext(isFireMode = true)
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)
            assertEquals(0, subscriptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDismissedModelRefocusesBeforeStorageReloadsThenFooterDoesNotFlash() = runTest {
        var subscriptions = 0
        val dismissalStore: HighUsageModelNoticeDismissalStore = mock()
        whenever(dismissalStore.dismissedModelIds).thenReturn(
            flow {
                subscriptions++
                if (subscriptions == 1) {
                    emit(setOf("claude-opus-4-8"))
                }
                awaitCancellation()
            },
        )
        val testee = plugin(dismissalStore)
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertFalse(awaitItem().visible)

            hostContext.value = duckAiContext(isInputFocused = false)
            expectNoEvents()

            hostContext.value = duckAiContext(isInputFocused = true)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenHighUsageModelIsVisibleThenMessageUsesSelectedModelShortName() = runTest {
        modelState.value = highUsageModel(shortName = "Opus")
        val footer = testee.createFooter(context, hostContext)

        assertTrue(footer.state.first().visible)

        assertEquals(
            "Opus uses limits up to 2–5x faster than basic models.",
            footer.view.findViewById<DaxTextView>(R.id.highUsageModelFooterMessage).text.toString(),
        )
    }

    @Test
    fun whenNoticeIsDismissedThenCurrentAndFutureFootersAreHidden() = runTest {
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)

            footer.view.findViewById<ImageView>(R.id.highUsageModelFooterDismiss).performClick()

            assertFalse(awaitItem().visible)
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(testee.createFooter(context, hostContext).state.first().visible)
    }

    @Test
    fun whenDismissIsTappedRepeatedlyThenFooterHidesImmediatelyAndPersistsOnce() = runTest {
        val dismissalStore: HighUsageModelNoticeDismissalStore = mock()
        whenever(dismissalStore.dismissedModelIds).thenReturn(MutableStateFlow(emptySet()))
        val testee = plugin(dismissalStore)
        modelState.value = highUsageModel()
        val footer = testee.createFooter(context, hostContext)

        footer.state.test {
            assertTrue(awaitItem().visible)
            val dismiss = footer.view.findViewById<ImageView>(R.id.highUsageModelFooterDismiss)

            dismiss.performClick()
            dismiss.performClick()

            assertFalse(awaitItem().visible)
            verify(dismissalStore, times(1)).dismiss("claude-opus-4-8")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun plugin(dismissalStore: HighUsageModelNoticeDismissalStore) = HighUsageModelFooterPlugin(
        modelManager = modelManager,
        duckChatFeature = feature,
        resolver = HighUsageModelNoticeResolver(),
        dismissalStore = dismissalStore,
        appCoroutineScope = coroutineRule.testScope,
    )

    private fun highUsageModel(shortName: String = "Claude Opus 4.8") = ModelState(
        selectedModelId = "claude-opus-4-8",
        selectedModelShortName = shortName,
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
}
