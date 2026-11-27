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

package com.duckduckgo.remote.messaging.impl.systemsettings

import android.os.Bundle
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.getActivityParams
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SystemSettingsScreenParams::class, screenName = "systemSettings")
class SystemSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var systemSettingsLauncher: SystemSettingsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getActivityParams(SystemSettingsScreenParams::class.java)?.let { systemSettingsLauncher.launch(this, it.screen) }
        finish()
    }
}
