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

package com.duckduckgo.common.ui.internal.ui.component.buttons

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostSecondaryButton
import com.duckduckgo.common.ui.compose.button.DaxDestructivePrimaryButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostAltButton
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButton
import com.duckduckgo.common.ui.internal.databinding.ComponentButtonsBinding
import com.duckduckgo.common.ui.internal.ui.appComponentsViewModel
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.duckduckgo.common.ui.DuckDuckGoTheme as AppTheme

@SuppressLint("NoFragment") // we don't use DI here
class ComponentButtonsFragment : Fragment() {

    private val appComponentsViewModel by appComponentsViewModel()
    private lateinit var binding: ComponentButtonsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ComponentButtonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val isDarkTheme = runBlocking { appComponentsViewModel.themeFlow.first() } == AppTheme.DARK
        setupComposeViews(view, isDarkTheme)
    }

    @Suppress("LongMethod")
    private fun setupComposeViews(
        view: View,
        isDarkTheme: Boolean,
    ) {
        // Primary
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_primary,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxPrimaryButton(text = "Primary Small", onClick = {})
                DaxPrimaryButton(
                    text = "Primary Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_add_16),
                )
                DaxPrimaryButton(
                    text = "Primary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxPrimaryButton(
                    text = "Primary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_device_desktop_16),
                )
                DaxPrimaryButton(text = "Primary Disabled", onClick = {}, enabled = false)
            }
        }

        // Secondary
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_secondary,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxSecondaryButton(text = "Secondary Small", onClick = {})
                DaxSecondaryButton(
                    text = "Secondary Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_bell),
                )
                DaxSecondaryButton(
                    text = "Secondary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxSecondaryButton(
                    text = "Secondary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_arrow_left_24),
                )
                DaxSecondaryButton(text = "Secondary Disabled", onClick = {}, enabled = false)
            }
        }

        // Destructive Primary
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_destructive_primary,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxDestructivePrimaryButton(text = "Destructive Small", onClick = {})
                DaxDestructivePrimaryButton(
                    text = "Destructive Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_bookmarks_16),
                )
                DaxDestructivePrimaryButton(
                    text = "Destructive Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxDestructivePrimaryButton(
                    text = "Destructive Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_reload_24),
                )
                DaxDestructivePrimaryButton(text = "Destructive Disabled", onClick = {}, enabled = false)
            }
        }

        // Ghost
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_ghost,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxGhostButton(text = "Ghost Small", onClick = {})
                DaxGhostButton(
                    text = "Ghost Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_bookmark_16),
                )
                DaxGhostButton(
                    text = "Ghost Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxGhostButton(
                    text = "Ghost Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_globe_16),
                )
                DaxGhostButton(text = "Ghost Disabled", onClick = {}, enabled = false)
            }
        }

        // Destructive Ghost Secondary
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_destructive_ghost_secondary,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxDestructiveGhostSecondaryButton(text = "Destructive Secondary Small", onClick = {})
                DaxDestructiveGhostSecondaryButton(
                    text = "Destructive Secondary Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_bookmarks_16),
                )
                DaxDestructiveGhostSecondaryButton(
                    text = "Destructive Secondary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxDestructiveGhostSecondaryButton(
                    text = "Destructive Secondary Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_reload_24),
                )
                DaxDestructiveGhostSecondaryButton(text = "Destructive Secondary Disabled", onClick = {}, enabled = false)
            }
        }

        // Destructive Ghost
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_destructive_ghost,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxDestructiveGhostButton(text = "Ghost Destructive Small", onClick = {})
                DaxDestructiveGhostButton(
                    text = "Ghost Destructive Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_arrow_left_24),
                )
                DaxDestructiveGhostButton(
                    text = "Ghost Destructive Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxDestructiveGhostButton(
                    text = "Ghost Destructive Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_bell),
                )
                DaxDestructiveGhostButton(text = "Ghost Destructive Disabled", onClick = {}, enabled = false)
            }
        }

        // Ghost Alt
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_button_ghost_alt,
            isDarkTheme = isDarkTheme,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DaxDestructiveGhostAltButton(text = "Ghost Alt Small", onClick = {})
                DaxDestructiveGhostAltButton(
                    text = "Ghost Alt Small",
                    onClick = {},
                    leadingIconPainter = painterResource(R.drawable.ic_arrow_left_24),
                )
                DaxDestructiveGhostAltButton(
                    text = "Ghost Alt Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                )
                DaxDestructiveGhostAltButton(
                    text = "Ghost Alt Large",
                    onClick = {},
                    size = DaxButtonSize.Large,
                    leadingIconPainter = painterResource(R.drawable.ic_bell),
                )
                DaxDestructiveGhostAltButton(text = "Ghost Alt Disabled", onClick = {}, enabled = false)
            }
        }

        // Icon Button
        view.setupThemedComposeView(
            id = com.duckduckgo.common.ui.internal.R.id.compose_icon_button,
            isDarkTheme = isDarkTheme,
        ) {
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_union),
                contentDescription = "Menu",
            )
        }
    }
}
