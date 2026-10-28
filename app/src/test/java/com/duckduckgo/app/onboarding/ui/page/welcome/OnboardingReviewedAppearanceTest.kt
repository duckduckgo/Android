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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.view.OnboardingDialogTitleView
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.common.ui.view.DaxSwitch
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.duckduckgo.fonts.R as FontsR
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingReviewedAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val current = mutableStateOf<OnboardingContentState?>(null)
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val fontScale = mutableStateOf(1f)
    private var drawingView: View? = null
    private val title = TextConfig.Literal("Choose the browsing experience that works for you")
    private val body = TextConfig.Literal("Body")
    private val options = listOf(object : Option {
        override val id = "first"
        override val label = "First"
        override val iconRes: Int? = null
    })
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cases = listOf(
        NativeTitleCase(R.id.welcomeContent, R.id.titleText, 20),
        NativeTitleCase(R.id.comparisonChartContent, R.id.comparisonChartTitle, 32),
        NativeTitleCase(R.id.widgetPromptContent, R.id.widgetPromptTitle, 32),
        NativeTitleCase(R.id.addressBarContent, R.id.addressBarTitle, 32),
        NativeTitleCase(R.id.inputScreenContent, R.id.inputScreenTitle, 32),
        NativeTitleCase(R.id.inputScreenPreviewContent, R.id.inputScreenPreviewTitle, 32),
        NativeTitleCase(R.id.reinstallerQuickSetupContent, R.id.quickSetupTitle, 32),
        NativeTitleCase(R.id.downloadReasonContent, R.id.downloadReasonTitle, 32),
        NativeTitleCase(R.id.preferenceSelectorContent, R.id.preferenceSelectorTitle, 52),
        NativeTitleCase(R.id.singleChoiceContent, R.id.singleChoiceTitle, 52),
        NativeTitleCase(R.id.duckAiStateContent, R.id.duckAiStateTitle, 52),
        NativeTitleCase(R.id.togglePositionContent, R.id.togglePositionTitle, 52),
        NativeTitleCase(R.id.importPasswordsContent, R.id.importPasswordsTitle, 32),
        NativeTitleCase(R.id.importCompleteContent, R.id.importCompleteTitle, 32),
    )

    @Test
    fun nativeTitleWidthsIncludeBothContentMarginsAndLocalPadding() {
        cases.forEach { case ->
            val reference = nativeReference(case)
            val card = reference.findViewById<ViewGroup>(R.id.cardView)
            val text = reference.findViewById<View>(case.title).findViewById<TextView>(R.id.onboardingDialogTitleSizingText)
            val bounds = Rect(0, 0, text.width, text.height)
            card.offsetDescendantRectToMyCoords(text, bounds)
            assertEquals("Native title inset for ${app.resources.getResourceEntryName(case.content)}", case.inset, bounds.left)
            assertEquals(card.width - case.inset * 2, text.width)
        }
        val quick = nativeReference(cases[6])
        val card = quick.findViewById<ViewGroup>(R.id.cardView)
        val icon = quick.findViewById<View>(R.id.quickSetupSwitchRowIcon)
        val bounds = Rect(0, 0, icon.width, icon.height)
        card.offsetDescendantRectToMyCoords(icon, bounds)
        assertEquals(24, bounds.left)
    }

    @Test
    fun choiceWidgetFootprintsMatchInitializedNativeWidgets() {
        val native = nativeReference(cases[6])
        val radio = descendants(nativeReference(cases[3])).filterIsInstance<RadioButton>().first()
        val switch = native.findViewById<View>(R.id.quickSetupSwitchRowSwitch)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme {
                    Column {
                        OnboardingRadioButton(true, Modifier.testTag("radio"))
                        OnboardingSwitch(true, Modifier.testTag("switch"))
                    }
                }
            }
        }
        listOf("radio" to radio, "switch" to switch).forEach { (tag, reference) ->
            val bounds = compose.onNodeWithTag(tag, true).fetchSemanticsNode().boundsInRoot
            assertEquals(tag, reference.measuredWidth.toFloat(), bounds.width, .1f)
            assertEquals(tag, reference.measuredHeight.toFloat(), bounds.height, .1f)
        }
    }

    @Test
    fun checkedSwitchTrackUsesInitializedNativeOnboardingPaletteInBothThemes() {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context()) {
                DuckDuckGoTheme(isDarkTheme = dark.value) {
                    drawingView = LocalView.current
                    OnboardingSwitch(true, Modifier.testTag("switch"))
                }
            }
        }
        listOf(false, true).forEach { night ->
            compose.runOnIdle { dark.value = night }
            val bounds = compose.onNodeWithTag("switch", true).fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val native = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_reinstaller_quick_setup, null)
                    .findViewById<DaxSwitch>(R.id.quickSetupSwitchRowSwitch)
                val expected = native.trackTintList!!.getColorForState(
                    intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
                    native.trackTintList!!.defaultColor,
                )
                val view = drawingView!!
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    assertEquals(expected, bitmap.getPixel(bounds.left.toInt() + 12, bounds.center.y.toInt()))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test
    fun inputChoiceRadiosAlignWhenCaptionsWrapDifferently() {
        show(configs()[4])
        listOf(1f, 1.3f).forEach { scale ->
            compose.runOnIdle { fontScale.value = scale }
            val options = compose.onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            ).fetchSemanticsNodes()
            assertEquals(2, options.size)
            assertEquals(options[0].boundsInRoot.bottom, options[1].boundsInRoot.bottom, .1f)
        }
    }

    @Test
    fun initializedNativeTypographyUsesEffectiveStyles() {
        val root = nativeReference(cases[6])
        val inflater = LayoutInflater.from(context())
        val chart = inflater.inflate(R.layout.include_brand_design_comparison_chart_row, null)
        val result = inflater.inflate(R.layout.include_brand_design_import_result_row, null)
        val preference = inflater.inflate(R.layout.include_brand_design_preference_row, null)
        val views = listOf(
            "address" to root.findViewById<ViewGroup>(R.id.topOmnibarContainer).children.filterIsInstance<DaxTextView>().single(),
            "input-search-caption" to root.findViewById<TextView>(R.id.inputScreenSearchOnlyCaption),
            "input-ai-caption" to root.findViewById<TextView>(R.id.inputScreenWithAiCaption),
            "input-description" to root.findViewById<TextView>(R.id.inputScreenDescription),
            "quick-switch" to root.findViewById<TextView>(R.id.quickSetupSwitchRowPrimaryText),
            "quick-edit-primary" to root.findViewById<TextView>(R.id.quickSetupEditRowPrimaryText),
            "quick-edit-secondary" to root.findViewById<TextView>(R.id.quickSetupEditRowSecondaryText),
            "chart-row" to chart.findViewById<TextView>(R.id.rowText),
            "download-label" to descendants(root).filterIsInstance<TextView>()
                .single { it.text.toString() == app.getString(R.string.downloadReasonOptionSearch) },
            "preference-description" to preference.findViewById<TextView>(R.id.quickSetupTwoLineSwitchRowSecondaryText),
            "toggle-caption" to root.findViewById<TextView>(R.id.togglePositionPictogramCaption),
            "parsing-body" to root.findViewById<TextView>(R.id.importCompleteBody),
            "import-result" to result.findViewById<TextView>(R.id.resultRowText),
        )
        shadowOf(Looper.getMainLooper()).idle()
        views.forEach { (name, text) ->
            val small = name in setOf("preference-description", "toggle-caption", "parsing-body")
            val display = name in setOf("address", "input-search-caption", "input-ai-caption", "quick-edit-secondary")
            val expectedFont = if (display) FontsR.font.ducksansdisplay_regular else FontsR.font.ducksansproduct_regular
            assertEquals(name, ResourcesCompat.getFont(context(), expectedFont), text.typeface)
            assertEquals(name, if (small) 14f else 16f, text.textSize, .1f)
            assertEquals(
                name,
                when (name) {
                    "preference-description" -> 18
                    "toggle-caption" -> 16
                    "parsing-body" -> 19
                    else -> 21
                },
                text.lineHeight,
            )
            assertEquals(
                name,
                when (name) { "toggle-caption" -> -1f
                    "parsing-body" -> 2f
                    else -> 1f },
                text.lineSpacingExtra,
                .001f,
            )
            assertEquals(name, name !in setOf("quick-edit-secondary", "toggle-caption"), text.includeFontPadding)
            assertEquals(name, 0f, text.letterSpacing, .001f)
            assertEquals(name, 400, text.typeface.weight)
        }
    }

    @Test
    fun composeCaptionAndResultGlyphsAndLineBoxesMatchInitializedNativeWidgets() {
        val native = nativeReference(cases[6])
        val inflater = LayoutInflater.from(context())
        show(configs()[3])
        compareTypography(
            native.findViewById<ViewGroup>(R.id.topOmnibarContainer).children.filterIsInstance<DaxTextView>().single(),
            app.getString(R.string.preOnboardingAddressBarPositionTop),
        )
        compose.runOnIdle { current.value = session(configs()[4]) }
        compareTypography(native.findViewById(R.id.inputScreenSearchOnlyCaption), app.getString(R.string.input_screen_user_pref_without_ai_updated))
        compareTypography(native.findViewById(R.id.inputScreenWithAiCaption), app.getString(R.string.input_screen_user_pref_with_ai_updated))
        compareTypography(native.findViewById(R.id.inputScreenDescription), "Body")
        compose.runOnIdle { current.value = session(configs()[6]) }
        compareTypography(native.findViewById(R.id.quickSetupSwitchRowPrimaryText), app.getString(R.string.preOnboardingReinstallSetDefaultLabel))
        compareTypography(native.findViewById(R.id.quickSetupEditRowPrimaryText), app.getString(R.string.preOnboardingReinstallAddressBarLabel))
        compareTypography(native.findViewById(R.id.quickSetupEditRowSecondaryText), app.getString(R.string.preOnboardingAddressBarPositionTop))
        compose.runOnIdle { current.value = session(configs()[1]) }
        val chart = inflater.inflate(R.layout.include_brand_design_comparison_chart_row, null)
        compareTypography(chart.findViewById(R.id.rowText), app.getString(ComparisonChartConfig.Ai.rows.first().textRes))
        compose.runOnIdle { current.value = session(configs()[7]) }
        val downloadLabel = app.getString(R.string.downloadReasonOptionSearch)
        compareTypography(descendants(native).filterIsInstance<TextView>().single { it.text.toString() == downloadLabel }, downloadLabel)
        val preferenceConfig = configs()[8] as ContentConfig.PreferenceSelector
        compose.runOnIdle {
            current.value = session(
                preferenceConfig.copy(
                    rows = preferenceConfig.rows.map {
                        it.copy(secondaryText = TextConfig.Literal("Description\nSecond line"))
                    },
                ),
            )
        }
        val preference = inflater.inflate(R.layout.include_brand_design_preference_row, null)
        compareTypography(preference.findViewById(R.id.quickSetupTwoLineSwitchRowPrimaryText), "Preference")
        compareTypography(preference.findViewById(R.id.quickSetupTwoLineSwitchRowSecondaryText), "Description\nSecond line")
        compose.runOnIdle {
            current.value = session((configs()[11] as ContentConfig.TogglePosition).copy(pictogramCaption = TextConfig.Literal("Body\nSecond line")))
        }
        compareTypography(native.findViewById(R.id.togglePositionPictogramCaption), "Body\nSecond line")
        compose.runOnIdle {
            val config = configs()[13] as ContentConfig.ImportComplete
            current.value = session(config.copy(parsingBody = TextConfig.Literal("Parsing body\nSecond line")))
        }
        compareTypography(native.findViewById(R.id.importCompleteBody), "Parsing body\nSecond line")
        compose.runOnIdle { current.value!!.presentImportOutcome(ImportCompleteContentState.Failed) }
        val result = inflater.inflate(R.layout.include_brand_design_import_result_row, null)
        compareTypography(result.findViewById(R.id.resultRowText), "Body")
    }

    private fun compareTypography(native: TextView, text: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val layout = textLayout(text)
        native.text = layout.layoutInput.text.text
        native.measure(
            View.MeasureSpec.makeMeasureSpec(layout.size.width + native.compoundPaddingLeft + native.compoundPaddingRight, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val nativeWidth = native.text.toString().lineSequence().maxOf { native.paint.measureText(it) }
        assertEquals(text, nativeWidth, layout.multiParagraph.intrinsics.maxIntrinsicWidth, .5f)
        assertEquals(text, native.measuredHeight - native.compoundPaddingTop - native.compoundPaddingBottom, layout.size.height)
        if (layout.lineCount > 1) {
            assertEquals(text, native.lineHeight.toFloat(), layout.getLineBaseline(1) - layout.getLineBaseline(0), 1f)
        }
    }

    private fun descendants(root: ViewGroup): Sequence<View> = root.children.flatMap {
        if (it is ViewGroup) sequenceOf(it) + descendants(it) else sequenceOf(it)
    }

    @Test
    fun composeTitleConstraintsMatchEveryNativeVariantIncludingLargeTypeAndRtl() {
        show(configs().first())
        listOf(1f, 1.6f).forEach { scale ->
            listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
                configs().zip(cases).forEach { (config, case) ->
                    compose.runOnIdle {
                        fontScale.value = scale
                        direction.value = layoutDirection
                        current.value = session(config)
                    }
                    val reference = nativeReference(case)
                    val nativeText = reference.findViewById<View>(case.title).findViewById<TextView>(R.id.onboardingDialogTitleSizingText)
                    val resolved = current.value!!.resolvedTitle(context())
                    assertEquals(config.javaClass.simpleName, nativeText.width, textLayout(resolved).size.width)
                }
            }
        }
    }

    @Test
    fun quickSetupRowsKeepNativeFullWidthAndTextInsets() {
        show(configs()[6])
        val reference = nativeReference(cases[6])
        val card = reference.findViewById<ViewGroup>(R.id.cardView)
        val row = reference.findViewById<View>(R.id.setDefaultBrowserItem)
        val rowText = reference.findViewById<TextView>(R.id.quickSetupSwitchRowPrimaryText)
        val bounds = Rect(0, 0, rowText.width, rowText.height)
        card.offsetDescendantRectToMyCoords(rowText, bounds)
        val label = app.getString(R.string.preOnboardingReinstallSetDefaultLabel)
        val composeRow = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val composeText = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(row.width.toFloat(), composeRow.width, .1f)
        assertEquals(bounds.left.toFloat(), composeText.left, .1f)
        assertEquals(rowText.width, textLayout(label).size.width)
    }

    @Test
    fun preferenceDescriptionAndImportParsingUseOnboardingSecondaryInBothThemes() {
        val preference = configs()[8]
        show(preference)
        listOf(false, true).forEach { night ->
            compose.runOnIdle {
                dark.value = night
                current.value = session(preference)
            }
            val reference = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_preference_row, null)
            val nativeDescription = reference.findViewById<TextView>(R.id.quickSetupTwoLineSwitchRowSecondaryText)
            assertEquals(nativeDescription.currentTextColor, textLayout("Description").layoutInput.style.color.toArgb())
            compose.runOnIdle { current.value = session(configs().last()) }
            val nativeImport = LayoutInflater.from(context()).inflate(R.layout.include_brand_design_import_complete, null)
                .findViewById<TextView>(R.id.importCompleteBody)
            assertEquals(nativeImport.currentTextColor, textLayout("Parsing body").layoutInput.style.color.toArgb())
        }
    }

    @Test
    fun downloadTilesPaintOnboardingRestSelectedAndPressedColorsInBothThemes() {
        show(configs()[7])
        listOf(false, true).forEach { night ->
            compose.runOnIdle {
                dark.value = night
                current.value = session(configs()[7])
            }
            val label = app.getString(downloadReasonLabel(DownloadReasonSelection.SEARCH))
            val tile = compose.onNodeWithText(label)
            assertEquals(context().getColorFromAttr(CommonR.attr.onboardingSurfaceTertiary), tileFill(label))
            tile.performTouchInput { down(center) }
            repeat((ViewConfiguration.getTapTimeout() + 31) / 16) {
                compose.runOnIdle { Snapshot.sendApplyNotifications() }
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            assertEquals(context().getColorFromAttr(CommonR.attr.onboardingAccentAltPrimary), tileFill(label))
            tile.performTouchInput { up() }
            assertEquals(context().getColorFromAttr(CommonR.attr.onboardingAccentAltPrimary), tileFill(label))
            val other = app.getString(downloadReasonLabel(DownloadReasonSelection.NO_AI))
            compose.onNodeWithText(other).performClick()
            assertEquals(context().getColorFromAttr(CommonR.attr.onboardingSurfaceTertiary), tileFill(label))
        }
    }

    private fun tileFill(label: String): Int {
        val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        return compose.runOnIdle {
            val view = checkNotNull(drawingView)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                bitmap.getPixel(bounds.center.x.toInt(), bounds.bottom.toInt() - 10)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun context() = ContextThemeWrapper(
        app,
        if (dark.value) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
    )

    private fun session(config: ContentConfig) = OnboardingContentState("test", config, ContentValueStore(), {}, true)

    private fun show(config: ContentConfig) {
        current.value = session(config)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context(),
                LocalDensity provides Density(1f, fontScale.value),
                LocalLayoutDirection provides direction.value,
            ) {
                DuckDuckGoTheme(isDarkTheme = dark.value) {
                    OnboardingTheme {
                        drawingView = LocalView.current
                        val state = current.value!!
                        Box(Modifier.width(364.dp).testTag("dialog")) {
                            DaxOnboardingDialog(
                                state.toString(),
                                state.resolvedTitle(LocalContext.current),
                                animate = false,
                                headerToActionsSpacing = 0.dp,
                                textHorizontalPadding = state.config.titleHorizontalPadding,
                                contentHorizontalPadding = state.config.contentHorizontalPadding,
                                content = {
                                    onContentReady(state::ready)
                                    OnboardingDialogContent(state, enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun configs(): List<ContentConfig> = listOf(
        ContentConfig.Welcome(title, body, null),
        ContentConfig.ComparisonChart(title, ComparisonChartConfig.Ai),
        ContentConfig.WidgetPrompt(title, body),
        ContentConfig.AddressBar(title, OmnibarType.SINGLE_TOP, true),
        ContentConfig.InputScreen(title, body, true),
        ContentConfig.InputScreenPreview(title, true, true, emptyList(), emptyList()),
        ContentConfig.QuickSetup(title, true, false, false, false, OmnibarType.SINGLE_TOP, true),
        ContentConfig.DownloadReason(title, body),
        ContentConfig.PreferenceSelector(
            title,
            listOf(
                ContentConfig.PreferenceSelector.Row(
                    OnboardingPreference.SEARCH_HISTORY,
                    null,
                    TextConfig.Literal("Preference"),
                    TextConfig.Literal("Description"),
                    true,
                ),
            ),
        ),
        ContentConfig.SingleChoice(title, body, options),
        ContentConfig.DuckAiState(title, body, options),
        ContentConfig.TogglePosition(
            title,
            CommonR.drawable.toggle_ai_chat_default_lighttheme,
            CommonR.drawable.toggle_ai_chat_default_darktheme,
            body,
            options,
        ),
        ContentConfig.ImportPasswords(title, body),
        ContentConfig.ImportComplete(title, title, TextConfig.Literal("Parsing body"), title, body, null),
    )

    private fun nativeReference(case: NativeTitleCase): ViewGroup {
        val context = ContextThemeWrapper(app, CommonR.style.Theme_DuckDuckGo_Light_Onboarding)
        val root = LayoutInflater.from(context).inflate(R.layout.pre_onboarding_dax_dialog_cta, null) as ViewGroup
        root.findViewById<ViewGroup>(R.id.cardContainer).children.forEach { it.visibility = View.GONE }
        root.findViewById<View>(case.content).visibility = View.VISIBLE
        root.findViewById<OnboardingDialogTitleView>(case.title).setTitle("Choose the browsing experience that works for you")
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private data class NativeTitleCase(val content: Int, val title: Int, val inset: Int)
}
