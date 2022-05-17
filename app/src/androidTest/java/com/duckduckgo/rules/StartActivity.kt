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

package com.duckduckgo.rules

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.duckduckgo.app.launch.LaunchBridgeActivity

class StartActivity {

    fun appStart() {
        val activityScenario: ActivityScenario<LaunchBridgeActivity>
        val intent = Intent(ApplicationProvider.getApplicationContext(), LaunchBridgeActivity::class.java)
        activityScenario = ActivityScenario.launch(intent)
    }
}
