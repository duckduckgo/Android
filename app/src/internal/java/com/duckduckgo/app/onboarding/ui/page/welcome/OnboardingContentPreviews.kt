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

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import com.duckduckgo.mobile.android.R as CommonR

@PreviewLightDark
@Composable
private fun OnboardingContentPreview(@PreviewParameter(OnboardingContentPreviews::class) config: ContentConfig) {
    DuckDuckGoTheme {
        val context = ContextThemeWrapper(
            LocalContext.current,
            if (DuckDuckGoTheme.colors.isDark) CommonR.style.Theme_DuckDuckGo_Dark_Onboarding else CommonR.style.Theme_DuckDuckGo_Light_Onboarding,
        )
        CompositionLocalProvider(LocalContext provides context) {
            OnboardingTheme {
                val state = remember(config) { OnboardingContentState("preview", config, ContentValueStore(), {}, true) }
                DaxOnboardingDialog(
                    "preview",
                    state.resolvedTitle(context),
                    animate = false,
                    headerImage = onboardingHeaderImage(state),
                    primaryCta = DaxOnboardingDialogCta(stringResource(R.string.preOnboardingInputScreenButton), state.primaryEnabled) {},
                    headerToActionsSpacing = 0.dp,
                    textHorizontalPadding = config.titleHorizontalPadding,
                    contentHorizontalPadding = config.contentHorizontalPadding,
                    content = {
                        onContentReady(state::ready)
                        OnboardingDialogContent(state, enabled)
                    },
                )
            }
        }
    }
}

private class OnboardingContentPreviews : PreviewParameterProvider<ContentConfig> {
    private val title = TextConfig.Literal("Make DuckDuckGo your own")
    private val body = TextConfig.Literal("Choose the browsing experience that works for you.")
    private val options = listOf(PreviewOption("first", "Keep Duck.ai"), PreviewOption("second", "Turn off Duck.ai"))
    private val suggestions = List(3) { DaxDialogIntroOption("Example suggestion ${it + 1}", CommonR.drawable.ic_find_search_16, "example") }
    override val values = sequenceOf(
        ContentConfig.Welcome(
            TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
            TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
            TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
        ),
        ContentConfig.ComparisonChart(TextConfig.Resource(ComparisonChartConfig.Ai.titleRes), ComparisonChartConfig.Ai),
        ContentConfig.WidgetPrompt(title, body),
        ContentConfig.AddressBar(title, OmnibarType.SINGLE_BOTTOM, true),
        ContentConfig.InputScreen(title, body, true),
        ContentConfig.InputScreenPreview(title, true, true, suggestions, suggestions),
        ContentConfig.QuickSetup(title, true, false, false, false, OmnibarType.SINGLE_TOP, true),
        ContentConfig.DownloadReason(title, body),
        ContentConfig.PreferenceSelector(
            title,
            listOf(
                ContentConfig.PreferenceSelector.Row(
                    OnboardingPreference.SEARCH_HISTORY,
                    CommonR.drawable.ic_find_search_24,
                    TextConfig.Literal("Search history"),
                    body,
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
        ContentConfig.ImportPasswords(
            TextConfig.Resource(R.string.preOnboardingImportPasswordsTitle),
            TextConfig.Resource(R.string.preOnboardingImportPasswordsBody),
        ),
        ContentConfig.ImportComplete(
            TextConfig.Resource(R.string.preOnboardingImportCompleteTitle),
            TextConfig.Resource(R.string.preOnboardingImportCompleteParsingTitle),
            TextConfig.Resource(R.string.preOnboardingImportCompleteParsingBody),
            TextConfig.Resource(R.string.preOnboardingImportCompleteFailedTitle),
            TextConfig.Resource(R.string.preOnboardingImportCompleteFailed),
            null,
        ),
    )
    private data class PreviewOption(override val id: String, override val label: String, override val iconRes: Int? = null) : Option
}
