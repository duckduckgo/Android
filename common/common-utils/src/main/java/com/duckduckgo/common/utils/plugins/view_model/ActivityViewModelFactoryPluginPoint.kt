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

package com.duckduckgo.common.utils.plugins.view_model

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@SingleIn(ActivityScope::class)
class ActivityViewModelFactoryPluginPoint @Inject constructor(
    private val injectorPlugins: Set<ViewModelFactoryPlugin>,
) : PluginPoint<ViewModelFactoryPlugin> {
    override fun getPlugins(): List<ViewModelFactoryPlugin> {
        return injectorPlugins.toList()
    }
}
