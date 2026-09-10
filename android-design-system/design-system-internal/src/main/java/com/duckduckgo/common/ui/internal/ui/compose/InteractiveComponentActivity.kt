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

package com.duckduckgo.common.ui.internal.ui.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.applyTheme
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView

/** Hosts a single full-screen interactive component playground, selected via [intent]. */
class InteractiveComponentActivity : AppCompatActivity() {

    @Suppress("DenyListedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val screen = intent.getStringExtra(EXTRA_SCREEN)
            ?.let { InteractiveComponentScreen.valueOf(it) }
            ?: InteractiveComponentScreen.DAX_DIALOG
        val isDarkTheme = intent.getBooleanExtra(EXTRA_DARK_THEME, false)
        applyTheme(if (isDarkTheme) DuckDuckGoTheme.DARK else DuckDuckGoTheme.LIGHT)
        super.onCreate(savedInstanceState)
        title = screen.title
        setContentView(
            ComposeView(this).apply {
                setupThemedComposeView(isDarkTheme) { screen.Content() }
            },
        )
    }

    companion object {
        private const val EXTRA_SCREEN = "extra_screen"
        private const val EXTRA_DARK_THEME = "extra_dark_theme"

        fun intent(
            context: Context,
            screen: InteractiveComponentScreen,
            isDarkTheme: Boolean,
        ): Intent = Intent(context, InteractiveComponentActivity::class.java).apply {
            putExtra(EXTRA_SCREEN, screen.name)
            putExtra(EXTRA_DARK_THEME, isDarkTheme)
        }
    }
}
