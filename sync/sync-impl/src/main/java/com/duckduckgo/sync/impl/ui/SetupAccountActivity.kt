/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.content.Context
import android.content.Intent
import android.os.*
import androidx.fragment.app.commitNow
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.app.global.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.mobile.android.ui.viewbinding.*
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.*

@InjectWith(ActivityScope::class)
class SetupAccountActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncSetupAccountBinding by viewBinding()
    private val viewModel: SetupAccountViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
    }

    private fun observeUiEvents() {
        supportFragmentManager.commitNow {
            replace(R.id.fragment_container_view, EnableSyncFragment.instance(), TAG_ENABLE_SYNC)
        }
    }

    companion object {
        private const val TAG_ENABLE_SYNC = "tag_enable_sync"
        fun intent(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java)
        }
    }
}
