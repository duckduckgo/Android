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
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupTwoLineSwitchRow
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w1000dp-h1200dp-450dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingSelectionAppearanceTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Application>(),
        CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
    )

    @Test
    fun iconlessPreferenceRowKeepsNativePrimaryTextStartMarginAt450dpi() {
        val config = ContentConfig.PreferenceSelector(
            title = TextConfig.Literal("Preference"),
            rows = listOf(
                ContentConfig.PreferenceSelector.Row(
                    preference = OnboardingPreference.SAFE_SEARCH,
                    iconRes = null,
                    primaryText = TextConfig.Literal("Safe Search"),
                    secondaryText = null,
                    initiallyEnabled = true,
                ),
            ),
        )
        val state = OnboardingContentState("preference", config, ContentValueStore(), {}, true)
        state.ready(animated = false)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                DuckDuckGoTheme {
                    OnboardingTheme {
                        Box(Modifier.width(with(LocalDensity.current) { 856.toDp() })) {
                            PreferenceSelectorContent(config, state, true)
                        }
                    }
                }
            }
        }

        val text = compose.onNodeWithText("Safe Search", useUnmergedTree = true).fetchSemanticsNode()
        val row = compose.onNodeWithText("Safe Search").fetchSemanticsNode()
        assertEquals(Role.Switch, row.config[SemanticsProperties.Role])

        val native = QuickSetupTwoLineSwitchRow(context).apply {
            setIcon(null)
            setPrimaryText("Safe Search")
            setSecondaryText(null)
        }
        native.measure(
            View.MeasureSpec.makeMeasureSpec(row.boundsInRoot.width.toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        native.layout(0, 0, native.measuredWidth, native.measuredHeight)

        val nativeText = native.findViewById<View>(R.id.quickSetupTwoLineSwitchRowPrimaryText)
        val nativeBounds = Rect()
        native.offsetDescendantRectToMyCoords(nativeText, nativeBounds)

        assertEquals(
            "A row with no icon must still indent its primary text like the native row",
            nativeBounds.left.toFloat(),
            text.boundsInRoot.left - row.boundsInRoot.left,
            .1f,
        )
    }
}
