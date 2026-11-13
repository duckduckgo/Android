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

package com.duckduckgo.duckchat.internal.clienttools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
class DuckAiClientToolsDevActivity : DuckDuckGoActivity() {

    private val viewModel: DuckAiClientToolsDevViewModel by bindViewModel()

    // duckchat-internal only ships in internal builds, so this screen is outside the production Compose restriction.
    @SuppressLint("NoSetContentUsage")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContent {
            DuckDuckGoTheme(isDarkTheme = isDarkThemeEnabled()) {
                val viewState by viewModel.viewState.collectAsState()
                DuckAiClientToolsDevScreen(
                    viewState = viewState,
                    onBack = ::finish,
                    onFlagToggled = viewModel::onFlagToggled,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, DuckAiClientToolsDevActivity::class.java)
    }
}
