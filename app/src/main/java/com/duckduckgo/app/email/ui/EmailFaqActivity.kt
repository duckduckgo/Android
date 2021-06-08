/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.activity_email_faq.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class EmailFaqActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_faq)
        setupToolbar(toolbar)

        val url = intent.getStringExtra(URL_EXTRA)

        simpleWebview?.let {
            it.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(true)
                setSupportZoom(true)
            }
        }

        url?.let {
            simpleWebview.loadUrl(it)
        }
    }

    companion object {
        const val URL_EXTRA = "URL_EXTRA"

        fun intent(context: Context, urlExtra: String): Intent {
            val intent = Intent(context, EmailFaqActivity::class.java)
            intent.putExtra(URL_EXTRA, urlExtra)
            return intent
        }
    }
}
