/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.browser.BrowserActivity
import timber.log.Timber

/**
 * Exists purely to pull out the intent extra and launch the query in a new tab.
 * This needs to be its own Activity so that we can customize the label that is user-facing, presented when the user selects some text.
 */
class SelectedTextSearchActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val query = extractQuery(intent)
        startActivity(BrowserActivity.intent(this, queryExtra = query))
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun extractQuery(intent: Intent?): String? {
        if (intent == null) return null

        val textSelectionQuery = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        if (textSelectionQuery != null) return textSelectionQuery

        val webSearchQuery = intent.getStringExtra(SearchManager.QUERY)
        if (webSearchQuery != null) return webSearchQuery

        Timber.w("SelectedTextSearchActivity launched with unexpected intent format")
        return null
    }
}
