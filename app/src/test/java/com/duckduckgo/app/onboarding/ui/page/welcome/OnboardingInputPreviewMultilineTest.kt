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
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [30], qualifiers = "w480dp-h1040dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingInputPreviewMultilineTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun switchingSearchToChatPaintsRetainedNewlinesOnSeparateLines() {
        val content = ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList())
        val session = OnboardingContentState("preview", content, ContentValueStore(), {}, true)
        session.ready(animated = false)
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    InputScreenPreviewContent(content, session, enabled = true)
                }
            }
        }
        val input = compose.onNode(hasSetTextAction())
        compose.onNodeWithText("Duck.ai").performClick()
        val text = "One\nTwo\nThree\nFour"
        val selection = TextRange(4, 7)
        compose.runOnIdle {
            session.inputEditor.edit {
                replace(0, length, text)
                this.selection = selection
            }
        }
        input.assertTextEquals(text)
        input.assertIsFocused()
        assertFourPaintedLines(text)

        compose.onNodeWithText("Search").performClick()
        input.assertTextEquals("One Two Three Four")
        compose.runOnIdle {
            assertEquals(text, session.inputEditor.text.toString())
            assertEquals(selection, session.inputEditor.selection)
        }
        input.assertIsFocused()
        assertEquals(selection, input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        val searchLayout = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(searchLayout) }
        assertEquals("Search must display a single line without changing the retained payload", 1, searchLayout.single().lineCount)

        compose.onNodeWithText("Duck.ai").performClick()
        input.assertTextEquals(text)
        input.assertIsFocused()
        assertEquals(selection, input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertFourPaintedLines(text)
    }

    @Test
    fun chatInputSettlesAtItsThreeLineMinimum() {
        val content = ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList())
        val session = OnboardingContentState("preview", content, ContentValueStore(), {}, true)
        session.ready(animated = false)
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    InputScreenPreviewContent(content, session, enabled = true)
                }
            }
        }
        val input = compose.onNode(hasSetTextAction())
        val searchHeight = input.fetchSemanticsNode().size.height
        val results = mutableListOf<TextLayoutResult>()
        input.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val lineHeight = results.single().let { it.getLineBottom(0) - it.getLineTop(0) }

        compose.onNodeWithText("Duck.ai").performClick()
        compose.waitForIdle()

        val chatHeight = input.fetchSemanticsNode().size.height
        assertTrue(
            "Duck.ai must grow by two lines: search=$searchHeight chat=$chatHeight line=$lineHeight",
            chatHeight - searchHeight >= 1.5f * lineHeight,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun readyPreviewOpensKeyboardAndKeepsModeOptionsCurrent() {
        val content = ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList())
        val session = OnboardingContentState("preview", content, ContentValueStore(), {}, true)
        session.ready(animated = false)
        val editors = mutableListOf<EditorInfo>()
        val connections = mutableListOf<InputConnection>()
        val interceptor = PlatformTextInputInterceptor { request, _ ->
            val info = EditorInfo()
            connections.add(request.createInputConnection(info))
            editors.add(info)
            awaitCancellation()
        }
        compose.setContent {
            InterceptPlatformTextInput(interceptor) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        InputScreenPreviewContent(content, session, enabled = true)
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.runOnIdle {
            assertTrue("Ready content must open the keyboard", editors.isNotEmpty())
            assertEquals(EditorInfo.IME_ACTION_SEARCH, editors.last().imeOptions and EditorInfo.IME_MASK_ACTION)
        }
        compose.onNodeWithText("Duck.ai").performClick()
        compose.runOnIdle {
            assertTrue(editors.last().inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            connections.last().setComposingText("One\nTwo", 1)
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("One\nTwo")
        compose.runOnIdle {
            connections.last().commitText("One\nTwo", 1)
            connections.last().finishComposingText()
        }
        compose.onNodeWithText("Search").performClick()
        compose.runOnIdle {
            assertEquals(0, editors.last().inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            assertEquals(EditorInfo.IME_ACTION_SEARCH, editors.last().imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    private fun assertFourPaintedLines(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals("Duck.ai must retain newline characters in the painted text", text, layout.layoutInput.text.text)
        assertEquals("Duck.ai must paint all four lines after changing modes", 4, layout.lineCount)
        for (line in 1 until 4) {
            assertTrue("Line $line must paint below the preceding line", layout.getLineTop(line) > layout.getLineTop(line - 1))
        }
    }
}
