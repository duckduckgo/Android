/*
 * Copyright 2019 The Android Open Source Project
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

package com.duckduckgo.common.ui.internal.ui.palette

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.internal.R
import com.duckduckgo.common.ui.internal.ui.appComponentsViewModel
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.common.ui.internal.ui.widget.compose.DaxColorAttributeListItem
import com.duckduckgo.common.ui.internal.ui.widget.compose.DaxColorDotColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.duckduckgo.common.ui.DuckDuckGoTheme as AppTheme

/** Fragment to display a list of subsystems that show the values of this app's theme. */
@SuppressLint("NoFragment") // we don't use DI here
class ColorPaletteFragment : Fragment() {

    private val appComponentsViewModel by appComponentsViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_components_color_palette, container, false)
    }

    @Suppress("DenyListedApi")
    override fun onViewCreated(
        view: View,
        savedInstanceBundle: Bundle?,
    ) {
        val isDarkTheme = runBlocking { appComponentsViewModel.themeFlow.first() } == AppTheme.DARK

        setupComposeViews(view, isDarkTheme)
    }

    private fun setupComposeViews(
        view: View,
        isDarkTheme: Boolean,
    ) {
        view.setupThemedComposeView(id = R.id.compose_background, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Background",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.backgrounds.background,
                    DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        view.setupThemedComposeView(id = R.id.compose_surface, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Surface",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.backgrounds.surface,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Container
        view.setupThemedComposeView(id = R.id.compose_container, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Container",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.backgrounds.container,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Destructive
        view.setupThemedComposeView(id = R.id.compose_destructive, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Destructive",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.text.destructive,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Container Disabled
        view.setupThemedComposeView(id = R.id.compose_container_disabled, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Container Disabled",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Lines
        view.setupThemedComposeView(id = R.id.compose_lines, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Lines",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.system.lines,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Accent Blue
        view.setupThemedComposeView(id = R.id.compose_accent_blue, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Accent Blue",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.brand.accentBlue,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Accent Yellow
        view.setupThemedComposeView(id = R.id.compose_accent_yellow, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Accent Yellow",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.brand.accentYellow,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Primary Text
        view.setupThemedComposeView(id = R.id.compose_primary_text, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Primary Text",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.textColors.primary,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Secondary Text
        view.setupThemedComposeView(id = R.id.compose_secondary_text, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Secondary Text",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.textColors.secondary,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Text Disabled
        view.setupThemedComposeView(id = R.id.compose_text_disabled, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Text Disabled",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.textColors.disabled,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }

        // Primary Icon - Note: There's no direct primaryIcon in DuckDuckGoColors, using text.primary as fallback
        view.setupThemedComposeView(id = R.id.compose_primary_icon, isDarkTheme = isDarkTheme) {
            DaxColorAttributeListItem(
                text = "Primary Icon",
                dotColors = DaxColorDotColors(
                    fillColor = DuckDuckGoTheme.colors.icons.primary,
                    strokeColor = DuckDuckGoTheme.colors.backgrounds.backgroundInverted,
                ),
            )
        }
    }
}
