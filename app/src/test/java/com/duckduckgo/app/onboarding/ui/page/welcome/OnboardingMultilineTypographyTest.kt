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
import android.os.Looper
import android.text.Layout
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.view.OnboardingDialogTitleView
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.utils.extensions.preventWidows
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [30], qualifiers = "w480dp-h1040dp-450dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingMultilineTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun welcomeBodyMatchesNativeGlyphWidthAndLineMetricsAtFractionalDensity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compareWelcomeNative(
            R.id.bodyText1,
            app.getString(R.string.preOnboardingWelcomeDialogBody1).preventWidows(),
        ) { text, modifier -> OnboardingTheme { OnboardingBody(text, modifier) } }
    }

    @Test
    fun largeWelcomeBodyMatchesNativeFontScalingAndLineMetrics() {
        RuntimeEnvironment.setFontScale(1.5f)
        val app = ApplicationProvider.getApplicationContext<Application>()
        compareWelcomeNative(
            R.id.bodyText1,
            app.getString(R.string.preOnboardingWelcomeDialogBody1).preventWidows(),
        ) { text, modifier -> OnboardingTheme { OnboardingBody(text, modifier) } }
    }

    @Test
    fun largeWelcomeTitleMatchesNativeFontScalingAndLineMetrics() {
        RuntimeEnvironment.setFontScale(1.5f)
        val app = ApplicationProvider.getApplicationContext<Application>()
        compareWelcomeNative(
            R.id.onboardingDialogTitleAnimatedText,
            app.getString(R.string.preOnboardingWelcomeDialogTitle).preventWidows(),
        ) { text, modifier ->
            OnboardingTheme {
                DaxOnboardingDialog(contentKey = "large-welcome", title = text, modifier = modifier, animate = false)
            }
        }
    }

    @Test
    fun welcomeTitleMatchesNativeGlyphWidthAndLineMetricsAtFractionalDensity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compareWelcomeNative(
            R.id.onboardingDialogTitleAnimatedText,
            app.getString(R.string.preOnboardingWelcomeDialogTitle).preventWidows(),
        ) { text, modifier ->
            OnboardingTheme {
                DaxOnboardingDialog(contentKey = "welcome", title = text, modifier = modifier, animate = false)
            }
        }
    }

    @Test
    fun multilineWelcomeTitleMatchesNativeGlyphWidthAndLineMetricsAtFractionalDensity() {
        compareWelcomeNative(R.id.onboardingDialogTitleAnimatedText, "Hi there.\nWelcome back.") { text, modifier ->
            OnboardingTheme {
                DaxOnboardingDialog(contentKey = "welcome", title = text, modifier = modifier, animate = false)
            }
        }
    }

    @Test
    fun threeLineWelcomeTitleMatchesNativeGlyphWidthAndLineMetricsAtFractionalDensity() {
        compareWelcomeNative(R.id.onboardingDialogTitleAnimatedText, "Hi there.\nWelcome back.\nBrowse privately.") { text, modifier ->
            OnboardingTheme {
                DaxOnboardingDialog(contentKey = "welcome", title = text, modifier = modifier, animate = false)
            }
        }
    }

    @Test
    @Config(qualifiers = "w480dp-h1040dp-hdpi")
    fun multilineTitleMatchesNativeSpacingAtHalfPixelDimension() {
        compareWelcomeNative(
            R.id.onboardingDialogTitleAnimatedText,
            "Hi there.\nWelcome back.\nTry this.",
            expectedDensity = 1.5f,
        ) { text, modifier ->
            OnboardingTheme {
                DaxOnboardingDialog(contentKey = "welcome", title = text, modifier = modifier, animate = false)
            }
        }
    }

    @Test
    fun welcomeTitleReservesNativeHeightBeforeFollowingBodyForOneTwoAndThreeLines() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = ContextThemeWrapper(app, CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        val native = LayoutInflater.from(context).inflate(R.layout.include_brand_design_dialog_welcome, null)
            .findViewById<OnboardingDialogTitleView>(R.id.titleText)
        val title = mutableStateOf("Hi there.")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        DaxOnboardingDialog(
                            contentKey = title.value,
                            title = title.value,
                            modifier = Modifier.width((900f / app.resources.displayMetrics.density).dp),
                            animate = false,
                            body = { Box(Modifier.size(1.dp).testTag("following-body")) },
                        )
                    }
                }
            }
        }
        listOf("Hi there.", "Hi there.\nWelcome back.", "Hi there.\nWelcome back.\nTry this.").forEach { text ->
            native.setTitle(text.replace("\n", "<br>"))
            native.snapTitle()
            shadowOf(Looper.getMainLooper()).idle()
            native.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val nativeSizing = native.findViewById<TextView>(R.id.onboardingDialogTitleSizingText)
            assertEquals(text, text.lineSequence().count(), nativeSizing.lineCount)
            compose.runOnIdle { title.value = text }
            val titleTop = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
            val bodyTop = compose.onNodeWithTag("following-body").fetchSemanticsNode().boundsInRoot.top
            val gap = DaxOnboardingDialogDefaults.TitleBodySpacing.value * app.resources.displayMetrics.density
            assertEquals(text, native.measuredHeight.toFloat(), bodyTop - titleTop - gap, .1f)
        }
    }

    @Test
    fun largeChartLabelsMatchNativeLineMetrics() {
        RuntimeEnvironment.setFontScale(1.5f)
        compareNative(
            R.layout.include_brand_design_comparison_chart_row,
            R.id.rowText,
            "First line\nSecond line",
        ) { OnboardingBodyParagraphStyle }
    }

    @Test
    fun narrowChartLabelsMatchInitializedNativeLineBoxesAtFractionalDensity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compareNative(
            R.layout.include_brand_design_comparison_chart_row,
            R.id.rowText,
            app.getString(ComparisonChartConfig.Ai.rows.first().textRes),
        ) { OnboardingBodyParagraphStyle }
    }

    @Test
    fun multilineDisplayCaptionsMatchInitializedNativeLineBoxesAtFractionalDensity() {
        compareNative(
            R.layout.include_brand_design_input_screen,
            R.id.inputScreenSearchOnlyCaption,
            "Search only\nPrivate browsing",
            OnboardingLabelSpanStyle,
        ) { OnboardingLabelParagraphStyle }
    }

    @Test
    fun multilinePreferencePrimaryMatchesInitializedNativeLineBoxesAtFractionalDensity() {
        compareNative(
            R.layout.include_brand_design_preference_row,
            R.id.quickSetupTwoLineSwitchRowPrimaryText,
            "Preference\nSecond line",
        ) { OnboardingBodyParagraphStyle.copy(lineHeight = 16.sp) }
    }

    @Test
    fun welcomeCardMatchesNativeTextAndButtonPlacementAtFractionalDensity() {
        compareWelcomeCard()
    }

    @Test
    @Config(qualifiers = "w480dp-h1040dp-hdpi")
    fun welcomeCardMatchesNativeTextAndButtonPlacementAtHalfPixelDensity() {
        compareWelcomeCard()
    }

    @Test
    fun largeWelcomeCardMatchesNativeTextAndButtonPlacement() {
        RuntimeEnvironment.setFontScale(1.5f)
        compareWelcomeCard()
    }

    private fun compareWelcomeCard() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = ContextThemeWrapper(app, CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        val root = LayoutInflater.from(context).inflate(R.layout.pre_onboarding_dax_dialog_cta, null)
        val native = root.findViewById<LinearLayout>(R.id.cardContainer)
        val title = app.getString(R.string.preOnboardingWelcomeDialogTitle).preventWidows()
        val body1 = app.getString(R.string.preOnboardingWelcomeDialogBody1).preventWidows()
        val body2 = app.getString(R.string.preOnboardingWelcomeDialogBody2).preventWidows()
        val primary = app.getString(R.string.preOnboardingDaxDialog1ButtonBrandDesign)
        val secondary = app.getString(R.string.preOnboardingDaxDialog1SecondaryButton)
        native.findViewById<OnboardingDialogTitleView>(R.id.titleText).apply {
            setTitle(title)
            snapTitle()
        }
        native.findViewById<TextView>(R.id.bodyText1).text = body1
        native.findViewById<TextView>(R.id.bodyText2).text = body2
        native.findViewById<View>(R.id.secondaryCta).visibility = View.VISIBLE
        shadowOf(Looper.getMainLooper()).idle()
        val density = app.resources.displayMetrics.density
        val width = (344 * density).roundToInt()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, width, native.measuredHeight)
        val state = DaxOnboardingDialogState()
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        DaxOnboardingDialog(
                            contentKey = "welcome",
                            title = title,
                            modifier = Modifier.width(((width + (4 * density).toInt()) / density).dp),
                            state = state,
                            animate = false,
                            headerToActionsSpacing = 0.dp,
                            textHorizontalPadding = 20.dp,
                            primaryCta = DaxOnboardingDialogCta(primary) {},
                            secondaryCta = DaxOnboardingDialogCta(secondary) {},
                            content = {
                                WelcomeContent(
                                    ContentConfig.Welcome(
                                        TextConfig.Literal(title),
                                        TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
                                        TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals("Welcome card content height", native.measuredHeight, state.contentHeightPx)
        val composeTitleTop = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        val nativeTitle = native.findViewById<View>(R.id.onboardingDialogTitleAnimatedText)
        fun topInCard(view: View): Int {
            var top = view.top
            var parent = view.parent
            while (parent !== native) {
                top += (parent as View).top
                parent = parent.parent
            }
            return top
        }
        listOf(R.id.bodyText1 to body1, R.id.bodyText2 to body2, R.id.primaryCta to primary, R.id.secondaryCta to secondary).forEach { (id, text) ->
            val nativeView = native.findViewById<View>(id)
            val composeTop = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
            val nativeInset = if (id == R.id.primaryCta || id == R.id.secondaryCta) {
                context.resources.getDimensionPixelSize(
                    CommonR.dimen.rebrandButtonSmallVerticalInset,
                )
            } else {
                0
            }
            assertEquals(text, (topInCard(nativeView) + nativeInset - topInCard(nativeTitle)).toFloat(), composeTop - composeTitleTop, .1f)
        }
    }

    private fun compareWelcomeNative(
        textId: Int,
        text: String,
        expectedDensity: Float = 2.8125f,
        content: @Composable (String, Modifier) -> Unit,
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = ContextThemeWrapper(app, CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        val native = LayoutInflater.from(context).inflate(R.layout.include_brand_design_dialog_welcome, null)
            .findViewById<TextView>(textId)
        shadowOf(Looper.getMainLooper()).idle()
        native.text = text
        native.measure(
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DuckDuckGoTheme {
                    content(text, Modifier.width((900f / app.resources.displayMetrics.density).dp))
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val details = "native size=${native.textSize} extra=${native.lineSpacingExtra} metrics=${native.paint.fontMetricsInt}"
        assertEquals(expectedDensity, app.resources.displayMetrics.density, .001f)
        assertEquals(details, ceil(Layout.getDesiredWidth(text, native.paint)), layout.multiParagraph.intrinsics.maxIntrinsicWidth, .1f)
        assertEquals(details, native.lineCount, layout.lineCount)
        assertEquals(details, native.measuredHeight, layout.size.height)
        assertEquals(details, native.baseline.toFloat(), layout.firstBaseline, .1f)
        for (line in 0 until native.lineCount) {
            assertEquals(details, native.layout.getLineBaseline(line).toFloat(), layout.getLineBaseline(line), .1f)
            assertEquals(
                details,
                native.layout.getLineBottom(line) - native.layout.getLineTop(line),
                (layout.getLineBottom(line) - layout.getLineTop(line)).toInt(),
            )
        }
    }

    private fun compareNative(
        layoutRes: Int,
        textId: Int,
        text: String,
        span: SpanStyle = SpanStyle(),
        paragraph: @Composable () -> ParagraphStyle,
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val context = ContextThemeWrapper(app, CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        val native = LayoutInflater.from(context).inflate(layoutRes, null)
            .findViewById<TextView>(textId)
        shadowOf(Looper.getMainLooper()).idle()
        native.text = text
        native.measure(
            View.MeasureSpec.makeMeasureSpec(461, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        DaxText(
                            AnnotatedString(text, span, paragraph()),
                            Modifier.width((461f / app.resources.displayMetrics.density).dp),
                            style = DuckDuckGoTheme.typography.onboardingBodyInContext,
                        )
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        assertEquals(2.8125f, app.resources.displayMetrics.density, .001f)
        assertEquals(2, native.lineCount)
        assertEquals(native.lineCount, layout.lineCount)
        val details = "native size=${native.textSize} extra=${native.lineSpacingExtra} metrics=${native.paint.fontMetricsInt}"
        assertEquals(details, native.measuredHeight, layout.size.height)
        assertEquals(details, native.getBaseline().toFloat(), layout.firstBaseline, .1f)
        assertEquals(details, native.layout.getLineBaseline(1).toFloat(), layout.getLineBaseline(1), .1f)
    }
}
