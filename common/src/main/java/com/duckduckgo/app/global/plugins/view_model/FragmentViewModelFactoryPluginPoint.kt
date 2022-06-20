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

package com.duckduckgo.app.global.plugins.view_model

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.FragmentScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(FragmentScope::class)
class FragmentViewModelFactoryPluginPoint @Inject constructor(
    private val injectorPlugins: DaggerSet<ViewModelFactoryPlugin>
) : PluginPoint<ViewModelFactoryPlugin> {
    override fun getPlugins(): List<ViewModelFactoryPlugin> {
        return injectorPlugins.toList()
    }
}
