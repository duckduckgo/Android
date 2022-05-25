/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.autofill.api.databinding.ActivityAutofillSettingsBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

@InjectWith(ActivityScope::class)
class AutofillSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityAutofillSettingsBinding by viewBinding()
    private val viewModel: AutofillSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AutofillSettingsActivity::class.java)
        }
    }
}
