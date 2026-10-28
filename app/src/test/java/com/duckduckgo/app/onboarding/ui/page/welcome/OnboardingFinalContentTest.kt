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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportResult
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
class OnboardingFinalContentTest {
    @get:Rule val compose = createComposeRule()
    private val store = ContentValueStore()
    private val interactions = mutableListOf<ContentInteraction>()
    private val current = mutableStateOf<OnboardingContentState?>(null)
    private val dialog = DaxOnboardingDialogState()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun duckAiAndToggleOptionsEmitTheirExactPayloadOnceAndDisposeOldSession() {
        val first = TestOption("first", "First")
        val second = TestOption("second", "Second")
        val third = TestOption("third", "Third")
        val initial = session(ContentConfig.DuckAiState(TextConfig.Literal("Duck.ai"), TextConfig.Literal("AI body"), listOf(first, second, third)))
        show(initial)
        compose.onNodeWithText("AI body").assertIsDisplayed()
        compose.onNodeWithText("First").performClick()
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithText("Third").performClick()
        assertEquals(listOf(first, second, third).map { ContentInteraction.SelectSingleChoiceOption(it) }, interactions)
        val toggle = session(
            ContentConfig.TogglePosition(
                TextConfig.Literal("Position"),
                CommonR.drawable.toggle_ai_chat_default_lighttheme,
                CommonR.drawable.toggle_ai_chat_default_darktheme,
                TextConfig.Literal("Caption"),
                listOf(second, first),
            ),
        )
        replace(toggle)
        compose.onNodeWithText("Caption").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("toggle-position-preview", true).fetchSemanticsNode().boundsInRoot
        assertEquals(128f, bounds.width, .1f)
        assertEquals(80f, bounds.height, .1f)
        compose.onNodeWithText("Second").performClick()
        initial.interact(ContentInteraction.SelectSingleChoiceOption(first))
        assertEquals(4, interactions.size)
        assertEquals(ContentInteraction.SelectSingleChoiceOption(second), interactions.last())
    }

    @Test
    fun importPasswordsRendersBodyAndConfiguredActionUnderHeader() {
        val state = session(ContentConfig.ImportPasswords(TextConfig.Literal("Import"), TextConfig.Literal("Import body")))
        show(state)
        compose.onNodeWithText("Import body").assertIsDisplayed()
        val withHeader = compose.onNodeWithText("Import", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        replace(session(ContentConfig.Welcome(TextConfig.Literal("Import"), TextConfig.Literal("Body"), null)))
        val withoutHeader = compose.onNodeWithText("Import", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertEquals(112f, withHeader - withoutHeader, 1f)
    }

    @Test
    fun initialOutcomeIsHeldThroughIntroThenTitleAndCtaChangeOnlyAfterFadeOut() {
        compose.mainClock.autoAdvance = false
        val config = import()
        val state = session(config)
        show(state, animate = true)
        compose.runOnIdle { state.stateFor(config).value = ImportCompleteContentState.Finished(12, 2) }
        advance(200)
        assertEquals(ImportCompleteContentState.Parsing, state.displayedImportOutcome)
        assertEquals("Parsing", state.resolvedTitle(context))
        assertTrue(!state.primaryEnabled)
        compose.runOnIdle { dialog.skip() }
        advance(64)
        compose.onNodeWithText("Parsing").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        assertTrue(state.importAlpha.value > 0f && state.importAlpha.value < 1f)
        advance(160)
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsEnabled()
        assertEquals(300.milliseconds, state.boundsDuration)
        assertTrue(state.importAlpha.value < 1f)
        advance(160)
        assertEquals(1f, state.importAlpha.value, 0f)
        assertCountBold(R.string.preOnboardingImportCompleteImported, 12)
        assertCountBold(R.string.preOnboardingImportCompleteSkipped, 2)
    }

    @Test
    fun terminalEntryIsEnabledImmediatelyAndZeroSkippedRowIsAbsent() {
        val config = import(PasswordImportResult.Terminal.Imported(5, 0))
        val state = session(config)
        assertTrue(state.primaryEnabled)
        show(state)
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithTag("import-parsing").assertDoesNotExist()
        assertCountBold(R.string.preOnboardingImportCompleteImported, 5)
        compose.onNodeWithText(context.getString(R.string.preOnboardingImportCompleteSkipped, "0")).assertDoesNotExist()
        replace(session(import(PasswordImportResult.Terminal.Failed), "failed"))
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("Failure row").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun rapidOutcomesCancelObsoleteReplacementAndLeavingCancelsFade() {
        compose.mainClock.autoAdvance = false
        val config = import()
        val state = session(config)
        show(state)
        advance(64)
        compose.runOnIdle { state.stateFor(config).value = ImportCompleteContentState.Finished(1, 1) }
        advance(64)
        compose.runOnIdle { state.stateFor(config).value = ImportCompleteContentState.Failed }
        advance(80)
        compose.onNodeWithText("Parsing").assertIsDisplayed()
        compose.onNodeWithText("Finished").assertDoesNotExist()
        advance(128)
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("Failure row").assertIsDisplayed()
        compose.runOnIdle { state.stateFor(config).value = ImportCompleteContentState.Parsing }
        advance(32)
        replace(session(ContentConfig.Welcome(TextConfig.Literal("Next"), TextConfig.Literal("Next body"), null)))
        advance(400)
        compose.onNodeWithText("Next").assertIsDisplayed()
        compose.onNodeWithText("Failure row").assertDoesNotExist()
        assertEquals(ImportCompleteContentState.Failed, state.displayedImportOutcome)
        assertTrue(state.disposed)
    }

    private fun assertCountBold(resource: Int, count: Int) {
        val text = context.getString(resource, count.toString())
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val span = results.single().layoutInput.text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(count.toString(), text.substring(span.start, span.end))
    }

    private fun import(result: PasswordImportResult? = null) = ContentConfig.ImportComplete(
        TextConfig.Literal("Finished"),
        TextConfig.Literal("Parsing"),
        TextConfig.Literal("Parsing body"),
        TextConfig.Literal("Failed"),
        TextConfig.Literal("Failure row"),
        result,
    )
    private fun session(config: ContentConfig, step: String = "test") = OnboardingContentState(step, config, store, interactions::add, true)
    private fun replace(state: OnboardingContentState) {
        compose.runOnIdle {
            current.value?.dispose()
            current.value = state
        }
    }

    private fun show(state: OnboardingContentState, animate: Boolean = false) {
        current.value = state
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    val bound = current.value!!
                    DaxOnboardingDialog(
                        contentKey = bound.toString(),
                        title = bound.resolvedTitle(LocalContext.current),
                        state = dialog,
                        animate = animate,
                        headerImage = onboardingHeaderImage(bound),
                        boundsAnimationDuration = bound.boundsDuration,
                        primaryCta = DaxOnboardingDialogCta("Continue", bound.primaryEnabled) {},
                        content = {
                            onContentReady(bound::ready)
                            OnboardingDialogContent(bound, enabled)
                        },
                    )
                }
            }
        }
    }

    private fun advance(ms: Long) {
        repeat(((ms + 15) / 16).toInt()) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }
    private data class TestOption(override val id: String, override val label: String, override val iconRes: Int? = null) : Option
}
