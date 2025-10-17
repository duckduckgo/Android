/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.common.ui.themepreview.ui.typography

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.themepreview.ui.appComponentsViewModel
import com.duckduckgo.common.ui.themepreview.ui.setupThemedComposeView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.duckduckgo.common.ui.DuckDuckGoTheme as AppTheme

/** Fragment to display a list of subsystems that show the values of this app's theme. */
@SuppressLint("NoFragment") // we don't use DI here
class TypographyFragment : Fragment() {

    private val appComponentsViewModel by appComponentsViewModel()

    private val loremIpsumShort = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
    private val loremIpsumLong =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_components_typography, container, false)
    }

    @Suppress("DenyListedApi")
    override fun onViewCreated(
        view: View,
        savedInstanceBundle: Bundle?,
    ) {
        val daxTextView = view.findViewById<DaxTextView>(R.id.typographyTitle)
        daxTextView.setTypography(Typography.Body1)

        val theme = runBlocking { appComponentsViewModel.themeFlow.first() }
        val isDarkTheme = theme == AppTheme.DARK || theme == AppTheme.BLACK

        setupComposeViews(view, isDarkTheme)
    }

    private fun setupComposeViews(view: View, isDarkTheme: Boolean) {
        // Title
        view.setupThemedComposeView(id = R.id.compose_title_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Title", style = DuckDuckGoTheme.typography.title)
        }
        view.setupThemedComposeView(id = R.id.compose_title_lorem, isDarkTheme = isDarkTheme) {
            DaxText(text = loremIpsumShort, style = DuckDuckGoTheme.typography.title)
        }

        // H1
        view.setupThemedComposeView(id = R.id.compose_h1_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance H1", style = DuckDuckGoTheme.typography.h1)
        }
        view.setupThemedComposeView(id = R.id.compose_h1_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.h1,
            )
        }

        // H2
        view.setupThemedComposeView(id = R.id.compose_h2_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance H2", style = DuckDuckGoTheme.typography.h2)
        }
        view.setupThemedComposeView(id = R.id.compose_h2_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.h2,
            )
        }

        // H3
        view.setupThemedComposeView(id = R.id.compose_h3_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance H3", style = DuckDuckGoTheme.typography.h3)
        }
        view.setupThemedComposeView(id = R.id.compose_h3_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.h3,
            )
        }

        // H4
        view.setupThemedComposeView(id = R.id.compose_h4_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance H4", style = DuckDuckGoTheme.typography.h4)
        }
        view.setupThemedComposeView(id = R.id.compose_h4_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.h4,
            )
        }

        // H5
        view.setupThemedComposeView(id = R.id.compose_h5_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance H5", style = DuckDuckGoTheme.typography.h5)
        }
        view.setupThemedComposeView(id = R.id.compose_h5_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.h5,
            )
        }

        // Body1
        view.setupThemedComposeView(id = R.id.compose_body1_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Body1", style = DuckDuckGoTheme.typography.body1)
        }
        view.setupThemedComposeView(id = R.id.compose_body1_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.body1,
            )
        }

        view.setupThemedComposeView(id = R.id.compose_body1_bold_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Body1 Bold", style = DuckDuckGoTheme.typography.body1Bold)
        }
        view.setupThemedComposeView(id = R.id.compose_body1_bold_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.body1Bold,
            )
        }

        view.setupThemedComposeView(id = R.id.compose_body1_mono_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Body1 Mono", style = DuckDuckGoTheme.typography.body1Mono)
        }
        view.setupThemedComposeView(id = R.id.compose_body1_mono_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.body1Mono,
            )
        }

        // Body2
        view.setupThemedComposeView(id = R.id.compose_body2_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Body2", style = DuckDuckGoTheme.typography.body2)
        }
        view.setupThemedComposeView(id = R.id.compose_body2_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.body2,
            )
        }

        view.setupThemedComposeView(id = R.id.compose_body2_bold_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Body2 Bold", style = DuckDuckGoTheme.typography.body2Bold)
        }
        view.setupThemedComposeView(id = R.id.compose_body2_bold_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.body2Bold,
            )
        }

        // Button
        view.setupThemedComposeView(id = R.id.compose_button_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Button", style = DuckDuckGoTheme.typography.button)
        }
        view.setupThemedComposeView(id = R.id.compose_button_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.button,
            )
        }

        // Caption
        view.setupThemedComposeView(id = R.id.compose_caption_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Caption", style = DuckDuckGoTheme.typography.caption)
        }
        view.setupThemedComposeView(id = R.id.compose_caption_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong,
                style = DuckDuckGoTheme.typography.caption,
            )
        }

        view.setupThemedComposeView(id = R.id.compose_caption_allcaps_label, isDarkTheme = isDarkTheme) {
            DaxText(text = "Text Appearance Caption All Caps".uppercase(), style = DuckDuckGoTheme.typography.caption)
        }
        view.setupThemedComposeView(id = R.id.compose_caption_allcaps_lorem, isDarkTheme = isDarkTheme) {
            DaxText(
                text = loremIpsumLong.uppercase(),
                style = DuckDuckGoTheme.typography.caption,
            )
        }

        view.setupThemedComposeView(id = R.id.compose_manual_typography, isDarkTheme = isDarkTheme) {
            DaxText(
                text = "Text Appearance Body 1 set manually",
                style = DuckDuckGoTheme.typography.body1,
                color = DuckDuckGoTheme.colors.destructive,
            )
        }
    }
}
