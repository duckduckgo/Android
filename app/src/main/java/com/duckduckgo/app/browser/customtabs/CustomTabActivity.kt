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

package com.duckduckgo.app.browser.customtabs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityCustomTabBinding
import com.duckduckgo.app.global.intentText
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
class CustomTabActivity : DuckDuckGoActivity() {

    private val binding: ActivityCustomTabBinding by viewBinding()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        println("TAG_ANA CustomTabActivity onCreate")

        val tabId = "CustomTab"
        val fragment = BrowserTabFragment.newInstanceForCustomTab(
            tabId = tabId,
            query = intent.intentText,
            skipHome = true,
            toolbarColor = intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0),
        )
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentTabContainer, fragment, tabId)
        transaction.commit()
    }
    companion object {

        fun intent(context: Context, oldIntent: Intent): Intent {
            // TODO Maybe sanitize the old intent?
            return Intent(context, CustomTabActivity::class.java).also {
                it.data = oldIntent.data
                it.putExtras(oldIntent)
                it.putExtra(Intent.EXTRA_TEXT, oldIntent.intentText)
            }
        }
    }
}