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

package com.duckduckgo.app.permissionsandprivacy

import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ActivityPermissionsAndPrivacyBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

@InjectWith(ActivityScope::class)
class PermissionsAndPrivacyActivity : DuckDuckGoActivity() {

    private val viewModel: PermissionsAndPrivacyViewModel by bindViewModel()
    private val binding: ActivityPermissionsAndPrivacyBinding by viewBinding()

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, PermissionsAndPrivacyActivity::class.java)
        }
    }
}
