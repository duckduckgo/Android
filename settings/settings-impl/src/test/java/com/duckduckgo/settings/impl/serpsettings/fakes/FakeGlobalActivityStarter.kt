/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.settings.impl.serpsettings.fakes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

class FakeGlobalActivityStarter : GlobalActivityStarter {
    val startedActivities = mutableListOf<ActivityParams>()
    val startedDeeplinkActivities = mutableListOf<GlobalActivityStarter.DeeplinkActivityParams>()
    var intentToReturn: Intent? = null

    override fun start(
        context: Context,
        params: ActivityParams,
        options: Bundle?,
    ) {
        startedActivities.add(params)
    }

    override fun start(
        context: Context,
        deeplinkActivityParams: GlobalActivityStarter.DeeplinkActivityParams,
        options: Bundle?,
    ) {
        startedDeeplinkActivities.add(deeplinkActivityParams)
    }

    override fun startIntent(context: Context, params: ActivityParams): Intent? {
        startedActivities.add(params)
        return intentToReturn
    }

    override fun startIntent(
        context: Context,
        deeplinkActivityParams: GlobalActivityStarter.DeeplinkActivityParams,
    ): Intent? {
        startedDeeplinkActivities.add(deeplinkActivityParams)
        return intentToReturn
    }
}
