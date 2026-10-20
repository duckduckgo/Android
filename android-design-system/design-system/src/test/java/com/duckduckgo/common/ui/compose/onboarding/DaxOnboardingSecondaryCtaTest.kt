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

package com.duckduckgo.common.ui.compose.onboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.DuckSansProduct
import com.duckduckgo.common.ui.compose.theme.LocalOnboardingColors
import com.duckduckgo.common.ui.compose.theme.Mandarin50
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxOnboardingSecondaryCtaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun secondaryCtaMatchesTheViewRebrandStyleInLightTheme() = assertAppearance(dark = false)

    @Test
    fun secondaryCtaMatchesTheViewRebrandStyleInDarkTheme() = assertAppearance(dark = true)

    @Test
    fun ctasFinishTheirFadeWithoutJumpingFromDisabledToEnabledColors() {
        compose.mainClock.autoAdvance = false
        val state = DaxOnboardingDialogState()
        lateinit var view: View
        var reference = Rect.Zero
        compose.setContent {
            view = LocalView.current
            DuckDuckGoTheme(isDarkTheme = false) {
                OnboardingTheme {
                    DaxOnboardingDialog(
                        contentKey = "fade",
                        title = "",
                        state = state,
                        primaryCta = DaxOnboardingDialogCta(text = "Continue", onClick = {}),
                        secondaryCta = DaxOnboardingDialogCta(text = "Skip for now", onClick = {}),
                    ) {
                        Box(Modifier.fillMaxWidth().height(48.dp).background(Mandarin50).onGloballyPositioned { reference = it.boundsInRoot() })
                    }
                }
            }
        }
        compose.assertCtasFinishWithoutColorJump(view, state) { reference }
    }

    private fun assertAppearance(dark: Boolean) {
        var view: View? = null
        var surface = Color.Unspecified
        compose.setContent {
            view = LocalView.current
            DuckDuckGoTheme(isDarkTheme = dark) {
                OnboardingTheme {
                    surface = LocalOnboardingColors.current.surfaceTertiary
                    DaxOnboardingDialog(
                        contentKey = "secondary",
                        title = "Title",
                        animate = false,
                        primaryCta = DaxOnboardingDialogCta(text = "Continue", onClick = {}),
                        secondaryCta = DaxOnboardingDialogCta(text = "Skip for now", onClick = {}),
                    )
                }
            }
        }
        compose.assertSecondaryCtaAppearance(checkNotNull(view), surface, dark)
    }
}

internal fun ComposeTestRule.assertSecondaryCtaAppearance(view: View, surface: Color, dark: Boolean = false) {
    val layouts = mutableListOf<TextLayoutResult>()
    onNode(
        hasText("Skip for now") and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val style = layouts.single().layoutInput.style
    assertEquals(18.sp, style.fontSize)
    assertEquals(FontWeight.Normal, style.fontWeight)
    assertEquals(DuckSansProduct, style.fontFamily)
    assertEquals(if (dark) Color.White else Color(0xF5000000), style.color)

    val bounds = onNodeWithText("Skip for now").fetchSemanticsNode().boundsInRoot
    val pixels = runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.asImageBitmap().toPixelMap()
    }
    val fill = (if (dark) Color(0x0AFFFFFF) else Color(0x0F000000)).compositeOver(surface)
    fun matches(color: Color): Boolean =
        maxOf(abs(color.red - fill.red), abs(color.green - fill.green), abs(color.blue - fill.blue)) < 0.01f
    val paintedRows = (bounds.top.toInt() until bounds.bottom.toInt()).filter { matches(pixels[bounds.center.x.toInt(), it]) }
    assertTrue("Secondary CTA must paint its grey fill", paintedRows.isNotEmpty())
    assertEquals("Rebrand small button has a 40dp painted surface", 40, paintedRows.last() - paintedRows.first() + 1)
    assertTrue("Secondary CTA must be filled through the horizontal centre", matches(pixels[bounds.left.toInt() + 2, bounds.center.y.toInt()]))
    assertTrue("Secondary CTA corners must be rounded", !matches(pixels[bounds.left.toInt() + 1, paintedRows.first() + 1]))
}

internal fun ComposeTestRule.assertCtasFinishWithoutColorJump(view: View, state: DaxOnboardingDialogState, referenceBounds: () -> Rect) {
    repeat(100) {
        mainClock.advanceTimeByFrame()
        waitForIdle()
        val primary = onNodeWithText("Continue").fetchSemanticsNode().boundsInRoot
        val (button, reference) = runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = bitmap.asImageBitmap().toPixelMap()
            val referenceCenter = referenceBounds().center
            pixels[primary.left.toInt() + 8, primary.center.y.toInt()] to pixels[referenceCenter.x.toInt(), referenceCenter.y.toInt()]
        }
        if (reference.blue in 0.4f..0.8f && reference.alpha > 0f) {
            assertTrue(state.isAnimating)
            onNodeWithText("Continue").assertIsNotEnabled()
            onNodeWithText("Skip for now").assertIsNotEnabled()
            val difference = maxOf(abs(button.red - reference.red), abs(button.green - reference.green), abs(button.blue - reference.blue))
            assertTrue("The CTA and identically colored content must fade together without a disabled palette: $difference", difference < 0.02f)
            return
        }
    }
    error("The fixture did not observe a partial content fade")
}
