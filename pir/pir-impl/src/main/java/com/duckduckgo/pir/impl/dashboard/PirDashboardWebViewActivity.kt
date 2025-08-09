/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard

import android.os.Bundle
import android.webkit.WebViewClient
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.api.dashboard.PirDashboardWebViewScreen
import com.duckduckgo.pir.impl.databinding.ActivityPirDashboardWebviewBinding
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDashboardWebViewScreen::class)
class PirDashboardWebViewActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ActivityPirDashboardWebviewBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupWebView()
    }

    private fun setupWebView() {
        // TODO: temporarily load DuckDuckGo Pro page until the actual PIR dashboard is ready
        binding.pirWebView.webViewClient = WebViewClient()
        binding.pirWebView.loadUrl("https://duckduckgo.com/")
    }
}
