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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
class ExchangeSyncCodeActivity : DuckDuckGoActivity() {
    private val syncUrl get() = requireNotNull(intent.getStringExtra(SYNC_URL_EXTRA_KEY)) {
        "Missing intent extra: '$SYNC_URL_EXTRA_KEY'"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = syncUrl
            setTextColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER)
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.MAGENTA)
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                addView(textView)
            },
        )
    }

    companion object {
        private const val SYNC_URL_EXTRA_KEY = "sync_url"
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"

        fun intent(
            context: Context,
            syncUrl: String,
            launchSource: String?,
        ): Intent {
            return Intent(context, ExchangeSyncCodeActivity::class.java).apply {
                putExtra(SYNC_URL_EXTRA_KEY, syncUrl)
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
