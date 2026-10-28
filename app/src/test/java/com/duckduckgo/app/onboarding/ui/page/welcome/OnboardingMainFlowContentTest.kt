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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
class OnboardingMainFlowContentTest {
    @get:Rule val compose = createComposeRule()
    private val store = ContentValueStore()
    private val interactions = mutableListOf<ContentInteraction>()
    private val submitted = mutableListOf<NewUserOnboardingEvent>()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun addressBarSelectionAndExternalReplaySubmitCurrentStoreWithoutInteraction() {
        val config = ContentConfig.AddressBar(TextConfig.Literal("Position"), OmnibarType.SINGLE_TOP, true)
        val state = show(config)
        compose.onNodeWithText(context.getString(R.string.preOnboardingAddressBarPositionBottom)).performClick().assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_BOTTOM)), submitted)
        compose.runOnIdle { state.stateFor(config).value = AddressBarContentState(OmnibarType.SPLIT) }
        compose.onNodeWithText(context.getString(R.string.preOnboardingAddressBarPositionSplit)).assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SPLIT), submitted.last())
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun addressBarWithoutSplitKeepsOnlyTwoOptions() {
        show(ContentConfig.AddressBar(TextConfig.Literal("Position"), OmnibarType.SINGLE_BOTTOM, false))
        compose.onNodeWithText(context.getString(R.string.preOnboardingAddressBarPositionSplit)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.preOnboardingAddressBarPositionBottom)).assertIsSelected()
    }

    @Test
    fun inputPickerChangesCurrentStateAndSubmitsWithoutInteractionReplay() {
        val config = ContentConfig.InputScreen(TextConfig.Literal("Input"), TextConfig.Literal("Choose your input"), false)
        val state = show(config)
        compose.onNodeWithText(context.getString(R.string.input_screen_user_pref_with_ai_updated)).performClick().assertIsSelected()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(NewUserOnboardingEvent.InputModeConfirmed(true)), submitted)
        compose.runOnIdle { state.stateFor(config).value = InputScreenContentState(false) }
        compose.onNodeWithText(context.getString(R.string.input_screen_user_pref_without_ai_updated)).assertIsSelected()
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun previewPreservesTextAndCursorChangesImeAndSubmitsTrimmedInput() {
        show(preview())
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("  hello world  ")
        input.performTextInputSelection(TextRange(5))
        compose.onNodeWithText("Duck.ai").performClick()
        input.assertTextEquals("  hello world  ")
        assertEquals(TextRange(5), input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertEquals(ImeAction.Default, input.fetchSemanticsNode().config[SemanticsProperties.ImeAction])
        input.performTextInput("\n")
        input.assertTextEquals("  hel\nlo world  ")
        compose.onNodeWithContentDescription(context.getString(R.string.preOnboardingInputModeDemoChatHint)).performClick()
        assertEquals(ContentInteraction.SubmitInputPreview("hel\nlo world", true, false), interactions.single())
        compose.onNodeWithText("Search").performClick()
        assertEquals(ImeAction.Search, input.fetchSemanticsNode().config[SemanticsProperties.ImeAction])
        input.performImeAction()
        assertEquals(ContentInteraction.SubmitInputPreview("hel\nlo world", false, false), interactions.last())
        input.performTextReplacement("   ")
        input.performImeAction()
        assertEquals(2, interactions.size)
    }

    @Test
    fun editorSurvivesStepReentryButNewHostStartsEmptyWithStoredMode() {
        val editor = OnboardingPreviewEditor()
        val config = preview()
        fun bind(previewEditor: OnboardingPreviewEditor) = OnboardingContentState("test", config, store, interactions::add, true, previewEditor)
        val current = mutableStateOf(bind(editor))
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    val state = current.value
                    DaxOnboardingDialog(contentKey = state.toString(), title = state.resolvedTitle(LocalContext.current), animate = false, content = {
                        onContentReady { state.ready(false) }
                        OnboardingDialogContent(state, enabled)
                    })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("retained draft")
        compose.onNode(hasSetTextAction()).performTextInputSelection(TextRange(3, 8))
        compose.onNodeWithText("Duck.ai").performClick()
        compose.runOnIdle { assertEquals("selection before leaving", TextRange(3, 8), editor.field.selection) }
        compose.runOnIdle {
            current.value.dispose()
            current.value = OnboardingContentState(
                "away", ContentConfig.Welcome(TextConfig.Literal("Away"), TextConfig.Literal("Body"), null),
                store, interactions::add, true, editor,
            )
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle {
            current.value.dispose()
            current.value = bind(editor)
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("retained draft")
        assertEquals(TextRange(3, 8), compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.onNodeWithText("Duck.ai").assertIsSelected()
        compose.runOnIdle {
            current.value.dispose()
            current.value = bind(OnboardingPreviewEditor())
        }
        assertEquals("", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Duck.ai").assertIsSelected()
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun suggestionsStayInaccessibleUntilLastFadeThenUsePayloadAndCurrentMode() {
        compose.mainClock.autoAdvance = false
        show(preview(), animateContent = true)
        settleShell()
        advance(1200)
        compose.onNodeWithText("search 0").assertDoesNotExist()
        compose.onNodeWithText("search 2").assertDoesNotExist()
        advance(1100)
        compose.onNodeWithText("search 0").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(ContentInteraction.SubmitInputPreview("payload search 0", false, true), interactions.single())
        compose.onNodeWithText("Duck.ai").performClick()
        advance(16)
        compose.onNodeWithText("chat 2").performScrollTo().performClick()
        assertEquals(ContentInteraction.SubmitInputPreview("payload chat 2", true, true), interactions.last())
    }

    @Test
    fun skipMakesEverySuggestionInteractiveAndDisposeRejectsStaleActions() {
        compose.mainClock.autoAdvance = false
        val state = show(preview(), animateContent = true)
        settleShell()
        advance(700)
        compose.runOnIdle { state.skip() }
        advance(16)
        advance(16)
        compose.onNodeWithText("search 2").performScrollTo().performClick()
        assertEquals(1, interactions.size)
        compose.runOnIdle { state.dispose() }
        compose.onNodeWithText("search 1").performClick()
        assertEquals(1, interactions.size)
    }

    @Test
    @Config(qualifiers = "w480dp-h500dp-mdpi")
    fun shortScreenDoesNotAutofocusInput() {
        show(preview())
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun widgetBodyRemainsVisibleWithMedia() {
        show(ContentConfig.WidgetPrompt(TextConfig.Literal("Widget"), TextConfig.Literal("Widget body")))
        compose.onNodeWithText("Widget body").assertIsDisplayed()
    }

    private fun advance(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun settleShell() {
        repeat(4) {
            advance(16)
            compose.waitForIdle()
        }
    }

    private fun preview() = ContentConfig.InputScreenPreview(
        TextConfig.Literal("Try it"),
        true,
        true,
        (0..2).map { DaxDialogIntroOption("search $it", CommonR.drawable.ic_find_search_16, "payload search $it") },
        (0..2).map { DaxDialogIntroOption("chat $it", CommonR.drawable.ic_find_search_16, "payload chat $it") },
    )

    private fun show(config: ContentConfig, animateContent: Boolean = false): OnboardingContentState {
        val state = OnboardingContentState("test", config, store, interactions::add, true)
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    DaxOnboardingDialog(
                        contentKey = "test",
                        title = state.resolvedTitle(LocalContext.current),
                        animate = false,
                        primaryCta = DaxOnboardingDialogCta("Continue") { state.submit()?.let(submitted::add) },
                        content = {
                            onContentReady { state.ready(animateContent) }
                            OnboardingDialogContent(state, enabled)
                        },
                    )
                }
            }
        }
        if (!compose.mainClock.autoAdvance) {
            repeat(4) {
                advance(16)
                compose.waitForIdle()
            }
            compose.runOnIdle { assertEquals("content is ready after the snapped shell", animateContent, state.readyAnimated) }
        }
        return state
    }
}
