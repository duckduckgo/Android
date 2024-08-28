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

package com.duckduckgo.app.generalsettings.showonapplaunch

import android.os.Bundle
import android.view.MenuItem
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityShowOnAppLaunchSettingBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(ShowOnAppLaunchScreenNoParams::class)
class ShowOnAppLaunchActivity : DuckDuckGoActivity() {

    private val binding: ActivityShowOnAppLaunchSettingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        configureUiEventHandlers()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun configureUiEventHandlers() {
        binding.lastOpenedTabCheckListItem.setOnClickListener {
            // TODO: Implement this
        }

        binding.newTabCheckListItem.setOnClickListener {
            // TODO: Implement this
        }

        binding.specificPageCheckListItem.setOnClickListener {
            // TODO: Implement this
        }
    }
}
