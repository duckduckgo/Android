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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityEmailUnsupportedBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

@InjectWith(ActivityScope::class)
class EmailProtectionUnsupportedActivity : DuckDuckGoActivity() {

    private val binding: ActivityEmailUnsupportedBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setupToolbar(toolbar)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, EmailProtectionUnsupportedActivity::class.java)
        }
    }
}
