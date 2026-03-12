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

package com.duckduckgo.edgetoedge.impl

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import com.duckduckgo.edgetoedge.api.EdgeToEdgeFeature
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealEdgeToEdge @Inject constructor(
    private val edgeToEdgeFeature: EdgeToEdgeFeature,
) : EdgeToEdge {

    override fun enableIfToggled(activity: ComponentActivity) {
        if (edgeToEdgeFeature.self().isEnabled()) {
            activity.enableEdgeToEdge()
        }
    }

    override fun isEnabled(): Boolean = edgeToEdgeFeature.self().isEnabled()
}
