/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.content_about_duck_duck_go.*
import kotlinx.android.synthetic.main.include_toolbar.*

class AboutDuckDuckGoActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_duck_duck_go)
        setupToolbar(toolbar)

        learnMoreLink.setOnClickListener {
            startActivity(BrowserActivity.intent(this, Url.ABOUT))
            finish()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AboutDuckDuckGoActivity::class.java)
        }
    }

}
