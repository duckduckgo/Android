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

package com.duckduckgo.common.ui.compose.button

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.DuckSansProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxBrandButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theDuckSansTypographyRendersTheLabelInDuckSans() {
        show(text = "Continue", typography = DaxBrandButtonTypography.DuckSans)

        val style = labelLayout("Continue").layoutInput.style
        assertEquals(DuckSansProduct, style.fontFamily)
        assertEquals(18.sp, style.fontSize)
    }

    @Test
    fun theStandardTypographyDoesNotRenderInDuckSans() {
        show(text = "Continue")

        assertTrue(labelLayout("Continue").layoutInput.style.fontFamily != DuckSansProduct)
    }

    private fun show(
        text: String,
        typography: DaxBrandButtonTypography = DaxBrandButtonTypography.Standard,
    ) {
        compose.setContent {
            DuckDuckGoTheme(isDarkTheme = false) {
                DaxBrandButton(text = text, onClick = {}, typography = typography)
            }
        }
    }

    private fun labelLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single()
    }
}
