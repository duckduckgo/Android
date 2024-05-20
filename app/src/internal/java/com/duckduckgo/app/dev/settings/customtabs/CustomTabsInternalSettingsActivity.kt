/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.customtabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityCustomTabsInternalSettingsBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
class CustomTabsInternalSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityCustomTabsInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        binding.load.setOnClickListener {
            val url = binding.urlInput.text
            if (url.isNotBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    openCustomTab("http://$url")
                } else {
                    openCustomTab(url)
                }
            }
        }
    }

    private fun openCustomTab(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        kotlin.runCatching {
            customTabsIntent.launchUrl(this, Uri.parse(url))
        }.onFailure {
            Toast.makeText(this, getString(R.string.customTabsInvalidUrl), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, CustomTabsInternalSettingsActivity::class.java)
        }
    }
}
