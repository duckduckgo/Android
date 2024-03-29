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

package com.duckduckgo.common.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.common.utils.plugins.view_model.ViewViewModelFactoryPluginPoint
import com.duckduckgo.di.scopes.ViewScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(ViewScope::class)
@ContributesBinding(ViewScope::class)
class ViewViewModelFactory @Inject constructor(
    private val viewModelFactoryPluginPoint: ViewViewModelFactoryPluginPoint,
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return viewModelFactoryPluginPoint.getPlugins().mapNotNull { it.create(modelClass) }
            .first()
    }
}
