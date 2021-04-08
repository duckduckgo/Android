/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps

import android.content.Context
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AppCategoryDetector {
    fun getAppCategory(packageName: String) : AppCategory
}

@ContributesBinding(AppObjectGraph::class)
class RealAppCategoryDetector @Inject constructor(context: Context) : AppCategoryDetector {
    private val packageManager = context.packageManager

    override fun getAppCategory(packageName: String): AppCategory {
        return packageManager.getApplicationInfo(packageName, 0).parseAppCategory()
    }
}
