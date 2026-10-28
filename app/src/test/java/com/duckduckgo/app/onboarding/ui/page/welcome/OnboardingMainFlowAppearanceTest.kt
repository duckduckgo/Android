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
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieCompositionFactory
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignComparisonChartBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.orchestrator.PasswordImportResult
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.ComparisonChartBinder
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.applyInputScreenPreviewInsets
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.applyInputScreenPreviewShape
import com.duckduckgo.app.onboarding.ui.page.welcome.binders.applyInputTextMode
import com.duckduckgo.app.onboarding.ui.view.OnboardingStepIndicatorView
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignAddressBarPositionPicker
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogStep
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.utils.extensions.applyBoldSpanTo
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w1000dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingMainFlowAppearanceTest {
    private var drawingView: View? = null

    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)
    private val width = mutableStateOf(480.dp)
    private val current = mutableStateOf<OnboardingContentState?>(null)
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun context(): Context = ContextThemeWrapper(
        app,
        if (dark.value) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
    )

    @Test
    fun addressImagesKeepNativeIntrinsicSizeAndPackedGapsOnWideHosts() {
        val config = ContentConfig.AddressBar(TextConfig.Literal("Position"), OmnibarType.SINGLE_TOP, true)
        show(config)
        listOf(360.dp, 900.dp).forEach { size ->
            compose.runOnIdle { width.value = size }
            val reference = ContextCompat.getDrawable(context(), R.drawable.mobile_toolbar_top_selected_brand_design_update_light)!!
            val images = OmnibarType.entries.map { compose.onNodeWithTag("address-preview-$it", true).fetchSemanticsNode().boundsInRoot }
            images.forEach {
                assertEquals(reference.intrinsicWidth.toFloat(), it.width, .1f)
                assertEquals(reference.intrinsicHeight.toFloat(), it.height, .1f)
            }
            images.zipWithNext().forEach { (left, right) -> assertTrue(right.left - left.right <= 72.1f) }
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun addressImagesKeepNativeIntrinsicDimensionsAt450dpi() {
        val config = ContentConfig.AddressBar(TextConfig.Literal("Position"), OmnibarType.SINGLE_TOP, true)
        show(config)
        val reference = ContextCompat.getDrawable(context(), R.drawable.mobile_toolbar_top_selected_brand_design_update_light)!!
        OmnibarType.entries.forEach { type ->
            val node = compose.onNodeWithTag("address-preview-$type", true).fetchSemanticsNode().boundsInRoot
            assertEquals(reference.intrinsicWidth.toFloat(), node.width, .1f)
            assertEquals(reference.intrinsicHeight.toFloat(), node.height, .1f)
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun addressImagePositionsMatchNativePickerAt450dpi() {
        val config = ContentConfig.AddressBar(TextConfig.Literal("Position"), OmnibarType.SINGLE_TOP, true)
        val state = OnboardingContentState("position", config, ContentValueStore(), {}, true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        Box(Modifier.width(with(LocalDensity.current) { 856.toDp() })) {
                            AddressBarContent(config, state, true)
                        }
                    }
                }
            }
        }

        val picker = BrandDesignAddressBarPositionPicker(context()).apply {
            setLightMode(true)
            isSplitOptionVisible = true
            setSelection(OmnibarType.SINGLE_TOP, false)
        }
        picker.measure(
            View.MeasureSpec.makeMeasureSpec(856, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)

        val nativeImages = listOf(
            R.id.topOmnibarToggleImage,
            R.id.bottomOmnibarToggleImage,
            R.id.splitOmnibarToggleImage,
        ).map { id ->
            val rect = Rect()
            picker.offsetDescendantRectToMyCoords(picker.findViewById(id), rect)
            rect.left.toFloat()
        }
        val composeImages = OmnibarType.entries.map {
            compose.onNodeWithTag("address-preview-$it", true).fetchSemanticsNode().boundsInRoot.left
        }

        nativeImages.zip(composeImages).forEach { (native, actual) ->
            assertEquals(native - nativeImages.first(), actual - composeImages.first(), .1f)
        }
    }

    @Test
    fun widgetUsesNativeWrapContentImageBeforeFrameMargins() {
        show(ContentConfig.WidgetPrompt(TextConfig.Literal("Widget"), TextConfig.Literal("Body")))
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_widget_prompt, null)
        native.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val image = compose.onNodeWithTag("widget-preview", true).fetchSemanticsNode().boundsInRoot
        val nativeImage = native.findViewById<View>(R.id.widgetPromptMediaImage)
        assertEquals(nativeImage.measuredWidth.toFloat(), image.width, .1f)
        assertEquals(nativeImage.measuredHeight.toFloat(), image.height, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun previewToggleWidthMatchesInitializedNativeTabsAt450dpi() {
        show(ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList()))
        val reference = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_input_screen_preview, null)
        val tabs = reference.findViewById<TabLayout>(R.id.inputModeToggle)
        shadowOf(Looper.getMainLooper()).idle()
        tabs.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val actual = compose.onNodeWithTag("input-mode-toggle", true).fetchSemanticsNode().boundsInRoot
        assertEquals(tabs.measuredWidth.toFloat(), actual.width, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun widgetImageMatchesNativeWrapContentAt450dpi() {
        show(ContentConfig.WidgetPrompt(TextConfig.Literal("Widget"), TextConfig.Literal("Body")))
        val reference = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_widget_prompt, null)
        reference.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val native = reference.findViewById<View>(R.id.widgetPromptMediaImage)
        val actual = compose.onNodeWithTag("widget-preview", true).fetchSemanticsNode().boundsInRoot
        assertEquals(native.measuredWidth.toFloat(), actual.width, .1f)
        assertEquals(native.measuredHeight.toFloat(), actual.height, .1f)
        val media = reference.findViewById<View>(R.id.widgetPromptMedia)
        val actualMedia = compose.onNodeWithTag("widget-media", true).fetchSemanticsNode().boundsInRoot
        assertEquals(media.measuredWidth.toFloat(), actualMedia.width, .1f)
        assertEquals(media.measuredHeight.toFloat(), actualMedia.height, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun comparisonHeaderIconMatchesBoundNativeDimensionsAt450dpi() {
        val config = ContentConfig.ComparisonChart(TextConfig.Literal("Comparison"), ComparisonChartConfig.Ai)
        show(config)
        val reference = IncludeBrandDesignComparisonChartBinding.inflate(LayoutInflater.from(context()))
        ComparisonChartBinder(reference).bind(config, BindScope(CoroutineScope(EmptyCoroutineContext), {}, {}))
        val actual = compose.onNodeWithTag("comparison-header-icon-${CommonR.drawable.ddg_logo_full_bleed}", true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(reference.comparisonChartHeaderRightIcon.layoutParams.width.toFloat(), actual.width, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun stepIndicatorMatchesNativeDimensionsAt450dpi() {
        assertStepIndicatorMatchesNativeDimensions()
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun largeStepIndicatorMatchesNativeDimensionsAt450dpi() {
        RuntimeEnvironment.setFontScale(1.5f)
        assertStepIndicatorMatchesNativeDimensions()
    }

    private fun assertStepIndicatorMatchesNativeDimensions() {
        val host = LayoutInflater.from(context()).inflate(R.layout.pre_onboarding_dax_dialog_cta, null)
        val native = host.findViewById<OnboardingStepIndicatorView>(R.id.stepIndicator).apply {
            visibility = View.VISIBLE
            setSteps(4, 3)
        }
        host.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        Box(Modifier.padding(top = 50.dp)) {
                            DaxOnboardingDialog(
                                contentKey = "step",
                                title = "Step",
                                step = DaxOnboardingDialogStep(3, (1..4).map { "$it of 4" }.toImmutableList()),
                                animate = false,
                            )
                        }
                    }
                }
            }
        }
        val actual = compose.onNodeWithTag("daxOnboardingStepIndicator", true).fetchSemanticsNode().boundsInRoot
        assertEquals(native.measuredWidth.toFloat(), actual.width, .1f)
        assertEquals(native.measuredHeight.toFloat(), actual.height, .1f)
        val nativeCard = host.findViewById<View>(R.id.cardView)
        val title = compose.onNodeWithText("Step").fetchSemanticsNode().boundsInRoot
        val composeCardTop = title.top - (28 * app.resources.displayMetrics.density + .5f).toInt()
        assertEquals((native.top - nativeCard.top).toFloat(), actual.top - composeCardTop, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun previewFieldHeightAndFillMatchInitializedNativeCardAt450dpi() {
        show(ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList()))
        val reference = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_input_screen_preview, null)
        val card = reference.findViewById<MaterialCardView>(R.id.inputModeDemoCard)
        card.applyInputScreenPreviewShape(true)
        reference.findViewById<EditText>(R.id.inputText).applyInputScreenPreviewInsets(true, reference.findViewById(R.id.inputModeDemoActionIcon))
        val actual = compose.onNodeWithTag("input-preview-card", true).fetchSemanticsNode().boundsInRoot
        shadowOf(Looper.getMainLooper()).idle()
        card.measure(
            View.MeasureSpec.makeMeasureSpec(actual.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(card.measuredHeight.toFloat(), actual.height, .1f)
        compose.runOnIdle {
            val view = checkNotNull(drawingView)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertEquals(card.cardBackgroundColor.defaultColor, bitmap.getPixel(actual.center.x.toInt(), actual.top.toInt() + 8))
            } finally {
                bitmap.recycle()
            }
        }
        compose.onNodeWithText("Duck.ai").performClick()
        reference.findViewById<EditText>(R.id.inputText).applyInputTextMode(false)
        val chat = compose.onNodeWithTag("input-preview-card", true).fetchSemanticsNode().boundsInRoot
        card.measure(
            View.MeasureSpec.makeMeasureSpec(chat.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(card.measuredHeight.toFloat(), chat.height, .1f)
    }

    @Test
    fun inputDescriptionUsesReferenceSecondaryColorAndSettledNativeLinesInBothThemes() {
        val text = "First line<br>Second line"
        show(ContentConfig.InputScreen(TextConfig.Literal("Input"), TextConfig.Literal(text), false))
        listOf(false, true).forEach { night ->
            compose.runOnIdle { dark.value = night }
            val result = textLayout("First line\nSecond\u00a0line")
            assertEquals(Color(context().getColorFromAttr(CommonR.attr.onboardingTextSecondary)), result.layoutInput.style.color)
            val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_input_screen, null)
                .findViewById<TextView>(R.id.inputScreenDescription)
            native.text = "First line\nSecond\u00a0line"
            shadowOf(Looper.getMainLooper()).idle()
            native.measure(
                View.MeasureSpec.makeMeasureSpec(result.size.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            assertEquals(2, native.lineCount)
            val nativeSpacing = native.layout.getLineBaseline(1) - native.layout.getLineBaseline(0)
            assertEquals(nativeSpacing.toFloat(), result.getLineBaseline(1) - result.getLineBaseline(0), .1f)
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun inputDescriptionBoldTextMatchesNativeFontSynthesis() {
        val html = "Normal <b>Bold Settings</b>."
        show(ContentConfig.InputScreen(TextConfig.Literal("Input"), TextConfig.Literal(html), false))
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_input_screen, null)
            .findViewById<TextView>(R.id.inputScreenDescription)
        native.text = html.html(context())
        shadowOf(Looper.getMainLooper()).idle()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(native.measuredWidth, textLayout("Normal Bold\u00a0Settings.").size.width)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun singleChoiceLabelBaselineMatchesInitializedNativeRow() {
        val option = object : Option {
            override val id = "first"
            override val label = "First option"
            override val iconRes: Int? = null
        }
        show(ContentConfig.SingleChoice(TextConfig.Literal("Choose"), TextConfig.Literal("Body"), listOf(option)))
        val label = compose.onNodeWithText(option.label, useUnmergedTree = true).fetchSemanticsNode()
        val row = checkNotNull(label.parent)
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_single_choice_row, null)
        native.findViewById<View>(R.id.singleChoiceRowIcon).visibility = View.GONE
        val text = native.findViewById<TextView>(R.id.singleChoiceRowPrimaryText).apply { this.text = option.label }
        shadowOf(Looper.getMainLooper()).idle()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(row.boundsInRoot.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, native.measuredWidth, native.measuredHeight)
        val baseline = label.boundsInRoot.top - row.boundsInRoot.top + textLayout(option.label).firstBaseline
        assertEquals("Native text height=${text.height}, Compose=${label.boundsInRoot.height}", (text.top + text.baseline).toFloat(), baseline, .1f)
    }

    @Test
    @Config(sdk = [30], qualifiers = "w1000dp-h1200dp-450dpi")
    fun importedResultLabelBaselineMatchesNativeAt450dpi() {
        val config = ContentConfig.ImportComplete(
            title = TextConfig.Literal("Finished"),
            parsingTitle = TextConfig.Literal("Parsing"),
            parsingBody = TextConfig.Literal("Parsing body"),
            failedTitle = TextConfig.Literal("Failed"),
            failedRow = TextConfig.Literal("Failure row"),
            result = PasswordImportResult.Terminal.Imported(imported = 12, skipped = 0),
        )
        show(config)

        val labelText = context().getString(R.string.preOnboardingImportCompleteImported, "12")
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_import_result_row, null)
        val nativeLabel = native.findViewById<TextView>(R.id.resultRowText).apply { text = labelText.applyBoldSpanTo("12") }
        shadowOf(Looper.getMainLooper()).idle()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(856, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, native.measuredWidth, native.measuredHeight)

        val root = compose.onNodeWithTag("import-outcome", true).fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText(labelText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rowTop = root.top + (20 * context().resources.displayMetrics.density).roundToInt()
        val baseline = label.top - rowTop + textLayout(labelText).firstBaseline
        assertEquals((nativeLabel.top + nativeLabel.baseline).toFloat(), baseline, .1f)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun inputModeRadioPositionMatchesNativeColumnGravity() {
        val config = ContentConfig.InputScreen(TextConfig.Literal("Input"), TextConfig.Literal("Body"), false)
        val state = OnboardingContentState("input", config, ContentValueStore(), {}, true).apply { ready(false) }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        Box(Modifier.width(with(LocalDensity.current) { 856.toDp() }).testTag("picker")) {
                            InputScreenContent(config, state, true)
                        }
                    }
                }
            }
        }
        val picker = compose.onNodeWithTag("picker", true).fetchSemanticsNode().boundsInRoot
        val native = BrandDesignInputScreenPicker(context()).apply {
            setLightMode(true)
            setSelection(false)
        }
        native.measure(
            View.MeasureSpec.makeMeasureSpec(picker.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, native.measuredWidth, native.measuredHeight)
        listOf(false to R.id.inputScreenSearchOnlyCheck, true to R.id.inputScreenWithAiCheck).forEach { (withAi, id) ->
            val expected = native.findViewById<View>(id)
            val actual = compose.onNodeWithTag("input-screen-radio-$withAi", true).fetchSemanticsNode().boundsInRoot
            assertEquals((expected.left + (expected.parent as View).left).toFloat(), actual.left - picker.left, .1f)
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun comparisonRowIconMatchesNativeVerticalGravity() {
        width.value = 304.dp
        val chart = ComparisonChartConfig.Browser(false)
        show(ContentConfig.ComparisonChart(TextConfig.Literal("Compare"), chart))
        val row = compose.onNodeWithTag("comparison-row-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("comparison-row-icon-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_comparison_chart_row, null)
        native.findViewById<TextView>(R.id.rowText).setText(chart.rows.first().textRes)
        shadowOf(Looper.getMainLooper()).idle()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(row.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, native.measuredWidth, native.measuredHeight)
        assertEquals(native.findViewById<View>(R.id.rowIcon).top.toFloat(), icon.top - row.top, .1f)
    }

    @Test
    fun darkPreviewSuggestionsMatchNativeWhenSystemIsLight() {
        assertSuggestionColorMatchesNative()
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-night-mdpi")
    fun darkPreviewSuggestionsMatchNativeWhenSystemIsDark() {
        assertSuggestionColorMatchesNative()
    }

    private fun assertSuggestionColorMatchesNative() {
        dark.value = true
        val option = com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption("Suggestion", CommonR.drawable.ic_find_search_16, "suggestion")
        show(ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, listOf(option), emptyList()))
        val native = com.duckduckgo.app.onboarding.ui.view.OnboardingSelectionButton(context())
        assertEquals(Color(native.currentTextColor), textLayout(option.optionText).layoutInput.style.color)
    }

    @Test
    @Config(qualifiers = "w1000dp-h1200dp-450dpi")
    fun optionButtonBaselinesMatchNativeIndependentInsets() {
        val options = listOf("First", "Second").map { label ->
            object : Option {
                override val id = label
                override val label = label
                override val iconRes: Int? = null
            }
        }
        show(ContentConfig.DuckAiState(TextConfig.Literal("Choose"), TextConfig.Literal("Body"), options))
        val baselinePositions = options.map { option ->
            val node = compose.onNodeWithText(option.label, useUnmergedTree = true).fetchSemanticsNode()
            node.boundsInRoot.top + textLayout(option.label).firstBaseline
        }
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_option_button_primary, null)
        shadowOf(Looper.getMainLooper()).idle()
        native.measure(
            View.MeasureSpec.makeMeasureSpec(856, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(native.measuredHeight.toFloat(), baselinePositions[1] - baselinePositions[0], .1f)
    }

    @Test
    fun previewIndicatorMovesBetweenNativeSizedTabsWithPrimaryText() {
        compose.mainClock.autoAdvance = false
        show(ContentConfig.InputScreenPreview(TextConfig.Literal("Try it"), true, true, emptyList(), emptyList()))
        advance(64)
        val initial = compose.onNodeWithTag("input-mode-indicator", true).fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("input-mode-toggle", true).fetchSemanticsNode().boundsInRoot
        assertEquals(36f, toggle.height, .1f)
        assertEquals(32f, initial.height, .1f)
        assertEquals(2f, initial.top - toggle.top, .1f)
        assertEquals(Color(context().getColorFromAttr(CommonR.attr.daxColorPrimaryText)), textLayout("Search").layoutInput.style.color)
        compose.onNodeWithText("Duck.ai").performClick()
        advance(80)
        val moving = compose.onNodeWithTag("input-mode-indicator", true).fetchSemanticsNode().boundsInRoot
        assertTrue(moving.left > initial.left)
        assertTrue(moving.left < initial.left + toggle.width / 2)
        advance(240)
        val end = compose.onNodeWithTag("input-mode-indicator", true).fetchSemanticsNode().boundsInRoot
        val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_input_screen_preview, null)
            .findViewById<TabLayout>(R.id.inputModeToggle)
        native.measure(
            View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertEquals(initial.left + native.getTabAt(0)!!.view.measuredWidth, end.left, 1f)
    }

    @Test
    fun selectingAiDuringEntranceWaitStartsPlaybackAndLeavingCancelsIt() {
        LottieCompositionFactory.fromRawResSync(context(), CommonR.raw.searchbox_with_ai_inactive)
        LottieCompositionFactory.fromRawResSync(context(), CommonR.raw.searchbox_with_ai_active)
        compose.mainClock.autoAdvance = false
        show(ContentConfig.InputScreen(TextConfig.Literal("Input"), TextConfig.Literal("Description"), false))
        advance(200 + 128)
        val waiting = aiPixels()
        advance(512)
        assertTrue("The initial playback waits two seconds", waiting.contentEquals(aiPixels()))
        compose.onNodeWithText(app.getString(R.string.input_screen_user_pref_with_ai_updated)).performClick()
        advance(240)
        val selected = aiPixels()
        advance(960)
        assertTrue("Selection starts playback before a new two-second delay could finish", !selected.contentEquals(aiPixels()))
        compose.runOnIdle {
            current.value!!.dispose()
            current.value = OnboardingContentState(
                "away", ContentConfig.Welcome(TextConfig.Literal("Away"), TextConfig.Literal("Body"), null),
                ContentValueStore(), {}, true,
            )
        }
        advance(3000)
        compose.onNodeWithTag("input-ai-preview", true).assertDoesNotExist()
        compose.onNodeWithText("Away").assertExists()
    }

    private fun aiPixels(): IntArray {
        val bounds = compose.onNodeWithTag("input-ai-preview", true).fetchSemanticsNode().boundsInRoot
        return compose.runOnIdle {
            val view = checkNotNull(drawingView)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = bounds.width.toInt()
            val height = bounds.height.toInt()
            IntArray(width * height).also {
                bitmap.getPixels(it, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
                bitmap.recycle()
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun advance(ms: Long) {
        repeat(((ms + 15) / 16).toInt()) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun show(config: ContentConfig) {
        current.value = OnboardingContentState("test", config, ContentValueStore(), {}, true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme(isDarkTheme = dark.value) {
                    OnboardingTheme {
                        drawingView = LocalView.current
                        val state = current.value!!
                        Box(Modifier.width(width.value)) {
                            DaxOnboardingDialog(state.toString(), state.resolvedTitle(LocalContext.current), animate = false, content = {
                                onContentReady { state.ready(false) }
                                OnboardingDialogContent(state, enabled)
                            })
                        }
                    }
                }
            }
        }
    }
}
