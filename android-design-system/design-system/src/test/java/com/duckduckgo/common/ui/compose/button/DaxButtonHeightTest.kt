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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxButtonHeightTest(
    private val case: Case,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aShortLabelKeepsTheVisibleTokenHeight() {
        show(text = "Continue", width = 360.dp)

        assertEquals(case.minHeight, buttonHeight())
    }

    @Test
    fun aWrappedLabelGrowsTheButtonByTheViewPaddingAndStaysCentred() {
        show(text = LongLabel, width = 120.dp)

        val label = compose.onNodeWithText(LongLabel, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag(ButtonTag).getUnclippedBoundsInRoot()
        val labelHeight = label.bottom - label.top
        assertTrue("The label must wrap for this fixture: $labelHeight", labelHeight + case.verticalPadding * 2 > case.minHeight)
        assertEquals(maxOf(case.minHeight, labelHeight + case.verticalPadding * 2).value, buttonHeight().value, 0.5f)
        assertEquals(((button.top + button.bottom) / 2).value, ((label.top + label.bottom) / 2).value, 0.5f)
    }

    @Test
    fun aTouchJustOutsideAButtonShorterThanTheMinimumTouchTargetStillClicksIt() {
        assumeTrue("Compose only extends touch input for buttons under 48dp", case.minHeight < 48.dp)
        var clicks = 0
        show(text = "Continue", width = 360.dp, onClick = { clicks++ })

        compose.onNodeWithTag(ButtonTag).performTouchInput { click(Offset(centerX, -3.dp.toPx())) }

        assertEquals(1, clicks)
    }

    private fun show(
        text: String,
        width: Dp,
        onClick: () -> Unit = {},
    ) {
        compose.setContent {
            DuckDuckGoTheme(isDarkTheme = false) {
                Box(Modifier.padding(24.dp).width(width).testTag(ButtonTag)) {
                    case.button(text, onClick, Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun buttonHeight(): Dp = compose.onNodeWithTag(ButtonTag).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    class Case(
        private val name: String,
        val minHeight: Dp,
        val verticalPadding: Dp,
        val button: @Composable (text: String, onClick: () -> Unit, modifier: Modifier) -> Unit,
    ) {
        override fun toString() = name
    }

    companion object {
        private const val ButtonTag = "button"
        private const val LongLabel = "Let's do it, show me how this works"

        // buttonSmallHeight 48 / buttonSmallTopPadding 8 and buttonLargeHeight 60 / buttonLargeTopPadding 14, less the 6dp MaterialButton inset.
        private val Small = 36.dp to 2.dp
        private val Large = 48.dp to 8.dp

        // rebrandButtonSmallHeight 48 / TopPadding 10 / VerticalInset 4 and rebrandButtonLargeHeight 56 / TopPadding 18 / VerticalInset 6.
        private val BrandSmall = 40.dp to 6.dp
        private val BrandLarge = 56.dp to 12.dp

        private fun case(
            name: String,
            heights: Pair<Dp, Dp>,
            button: @Composable (String, () -> Unit, Modifier) -> Unit,
        ) = arrayOf<Any>(Case(name, heights.first, heights.second, button))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            case("primary small", Small) { t, c, m -> DaxPrimaryButton(text = t, onClick = c, modifier = m) },
            case("primary large", Large) { t, c, m -> DaxPrimaryButton(text = t, onClick = c, modifier = m, size = DaxButtonSize.Large) },
            case("secondary small", Small) { t, c, m -> DaxSecondaryButton(text = t, onClick = c, modifier = m) },
            case("secondary large", Large) { t, c, m -> DaxSecondaryButton(text = t, onClick = c, modifier = m, size = DaxButtonSize.Large) },
            case("ghost small", Small) { t, c, m -> DaxGhostButton(text = t, onClick = c, modifier = m) },
            case("ghost large", Large) { t, c, m -> DaxGhostButton(text = t, onClick = c, modifier = m, size = DaxButtonSize.Large) },
            case("brand small", BrandSmall) { t, c, m -> DaxBrandButton(text = t, onClick = c, modifier = m) },
            case("brand large", BrandLarge) { t, c, m -> DaxBrandButton(text = t, onClick = c, modifier = m, size = DaxButtonSize.Large) },
        )
    }
}
