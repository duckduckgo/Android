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
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.component.core.text.DaxTextPrimary
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.mobile.android.R

/** Fragment to display a list of subsystems that show the values of this app's theme. */
@SuppressLint("NoFragment") // we don't use DI here
class TypographyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_components_typography, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceBundle: Bundle?,
    ) {
        val daxTextView = view.findViewById<DaxTextView>(R.id.typographyTitle)
        daxTextView.setTypography(Typography.Body1)
        
        setupComposeViews(view)
    }
    
    private fun setupComposeViews(view: View) {
        // Title
        setupComposeView(view, R.id.compose_title_label) { 
            DaxTextPrimary(text = "Text Appearance Title", style = DuckDuckGoTheme.typography.title)
        }
        setupComposeView(view, R.id.compose_title_lorem) { 
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", style = DuckDuckGoTheme.typography.title)
        }
        
        // H1
        setupComposeView(view, R.id.compose_h1_label) {
            DaxTextPrimary(text = "Text Appearance H1", style = DuckDuckGoTheme.typography.h1)
        }
        setupComposeView(view, R.id.compose_h1_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.h1)
        }
        
        // H2
        setupComposeView(view, R.id.compose_h2_label) {
            DaxTextPrimary(text = "Text Appearance H2", style = DuckDuckGoTheme.typography.h2)
        }
        setupComposeView(view, R.id.compose_h2_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.h2)
        }
        
        // H3
        setupComposeView(view, R.id.compose_h3_label) {
            DaxTextPrimary(text = "Text Appearance H3", style = DuckDuckGoTheme.typography.h3)
        }
        setupComposeView(view, R.id.compose_h3_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.h3)
        }
        
        // H4
        setupComposeView(view, R.id.compose_h4_label) {
            DaxTextPrimary(text = "Text Appearance H4", style = DuckDuckGoTheme.typography.h4)
        }
        setupComposeView(view, R.id.compose_h4_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.h4)
        }
        
        // H5
        setupComposeView(view, R.id.compose_h5_label) {
            DaxTextPrimary(text = "Text Appearance H5", style = DuckDuckGoTheme.typography.h5)
        }
        setupComposeView(view, R.id.compose_h5_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.h5)
        }
        
        // Body1
        setupComposeView(view, R.id.compose_body1_label) {
            DaxTextPrimary(text = "Text Appearance Body1", style = DuckDuckGoTheme.typography.body1)
        }
        setupComposeView(view, R.id.compose_body1_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.body1)
        }
        
        // Body1 Bold (using body1 style for now as bold variant doesn't exist)
        setupComposeView(view, R.id.compose_body1_bold_label) {
            DaxTextPrimary(text = "Text Appearance Body1 Bold", style = DuckDuckGoTheme.typography.body1)
        }
        setupComposeView(view, R.id.compose_body1_bold_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.body1)
        }
        
        // Body1 Mono (using body1 style for now as mono variant doesn't exist)
        setupComposeView(view, R.id.compose_body1_mono_label) {
            DaxTextPrimary(text = "Text Appearance Body1 Mono", style = DuckDuckGoTheme.typography.body1)
        }
        setupComposeView(view, R.id.compose_body1_mono_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.body1)
        }
        
        // Body2
        setupComposeView(view, R.id.compose_body2_label) {
            DaxTextPrimary(text = "Text Appearance Body2", style = DuckDuckGoTheme.typography.body2)
        }
        setupComposeView(view, R.id.compose_body2_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.body2)
        }
        
        // Body2 Bold (using body2 style for now as bold variant doesn't exist)
        setupComposeView(view, R.id.compose_body2_bold_label) {
            DaxTextPrimary(text = "Text Appearance Body2 Bold", style = DuckDuckGoTheme.typography.body2)
        }
        setupComposeView(view, R.id.compose_body2_bold_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.body2)
        }
        
        // Button
        setupComposeView(view, R.id.compose_button_label) {
            DaxTextPrimary(text = "Text Appearance Button", style = DuckDuckGoTheme.typography.button)
        }
        setupComposeView(view, R.id.compose_button_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.button)
        }
        
        // Caption
        setupComposeView(view, R.id.compose_caption_label) {
            DaxTextPrimary(text = "Text Appearance Caption", style = DuckDuckGoTheme.typography.caption)
        }
        setupComposeView(view, R.id.compose_caption_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.caption)
        }
        
        // Caption All Caps (using caption style for now as all caps variant doesn't exist)
        setupComposeView(view, R.id.compose_caption_allcaps_label) {
            DaxTextPrimary(text = "Text Appearance Caption All Caps", style = DuckDuckGoTheme.typography.caption)
        }
        setupComposeView(view, R.id.compose_caption_allcaps_lorem) {
            DaxTextPrimary(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", style = DuckDuckGoTheme.typography.caption)
        }
        
        // Manual Typography
        setupComposeView(view, R.id.compose_manual_typography) {
            DaxTextPrimary(text = "Text Appearance Body 1 set manually", style = DuckDuckGoTheme.typography.body1)
        }
    }
    
    private fun setupComposeView(view: View, id: Int, content: @Composable () -> Unit) {
        view.findViewById<ComposeView>(id)?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DuckDuckGoTheme {
                    content()
                }
            }
        }
    }
}
