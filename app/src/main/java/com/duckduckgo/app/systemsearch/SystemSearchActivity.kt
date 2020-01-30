/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber
import javax.inject.Inject

class SystemSearchActivity : DuckDuckGoActivity() {

    private val viewModel: SystemSearchViewModel by bindViewModel()

    @Inject
    lateinit var pixel: Pixel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_search)
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        Timber.i("onNewIntent: $newIntent")

        val intent = newIntent ?: return
        if (launchedFromWidget(intent)) {
            Timber.w("System search launched from widget")
            pixel.fire(Pixel.PixelName.WIDGET_LAUNCHED)
            return
        }
    }

    private fun launchedFromWidget(intent: Intent): Boolean {
        return intent.getBooleanExtra(WIDGET_SEARCH_EXTRA, false)
    }

    companion object {

        fun intent(context: Context, widgetSearch: Boolean = false): Intent {
            val intent = Intent(context, SystemSearchActivity::class.java)
            intent.putExtra(WIDGET_SEARCH_EXTRA, widgetSearch)
            return intent
        }

        const val WIDGET_SEARCH_EXTRA = "WIDGET_SEARCH_EXTRA"
    }
}