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

package com.duckduckgo.app.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.SettingsPageFeature
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SettingsScreenNoParams::class, screenName = "settings")
class SettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var settingsPageFeature: SettingsPageFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (settingsPageFeature.newSettingsPage().isEnabled()) {
            startActivity(NewSettingsActivity.intent(this))
        } else {
            startActivity(LegacySettingsActivity.intent(this))
        }
        finish()
    }

    companion object {
        const val LAUNCH_FROM_NOTIFICATION_PIXEL_NAME = "LAUNCH_FROM_NOTIFICATION_PIXEL_NAME"

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}
