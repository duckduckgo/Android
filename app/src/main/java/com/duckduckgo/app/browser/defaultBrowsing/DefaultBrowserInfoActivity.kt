/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultBrowsing

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import kotlinx.android.synthetic.main.activity_default_browser_info.*
import javax.inject.Inject

class DefaultBrowserInfoActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var defaultBrowserDetector: DefaultBrowserDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_default_browser_info)
        configureUiEventHandlers()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun configureUiEventHandlers() {
        dismissButton.setOnClickListener { exitActivity() }
        launchSettingsButton.setOnClickListener { launchDefaultAppActivity() }
        defaultBrowserIllustration.setOnClickListener { launchDefaultAppActivity() }
    }

    override fun onResume() {
        super.onResume()

        if (defaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()) {
            finish()
        }
    }

    override fun onBackPressed() {
        exitActivity()
    }

    private fun exitActivity() {
        launchSettingsButton.text = ""
        finishAfterTransition()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DefaultBrowserInfoActivity::class.java)
        }
    }
}
