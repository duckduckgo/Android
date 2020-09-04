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

package com.duckduckgo.app.settings.developer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity

class DeveloperSettingsActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeveloperSettingsActivity::class.java)
        }
    }

}
