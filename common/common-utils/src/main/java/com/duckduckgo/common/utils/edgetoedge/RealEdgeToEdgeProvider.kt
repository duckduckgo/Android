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

package com.duckduckgo.common.utils.edgetoedge

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = EdgeToEdgeProvider::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
class RealEdgeToEdgeProvider @Inject constructor(
    private val edgeToEdgeFeature: EdgeToEdgeFeature,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appScope: CoroutineScope,
) : EdgeToEdgeProvider, MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        appScope.launch {
            withContext(dispatchers.io()) {
                edgeToEdgeFeature.self().isEnabled()
                edgeToEdgeFeature.browser().isEnabled()
                edgeToEdgeFeature.settings().isEnabled()
            }
        }
    }

    override fun isEnabled(bucket: EdgeToEdgeBucket): Boolean = if (edgeToEdgeFeature.self().isEnabled()) {
        val bucketToggle = when (bucket) {
            EdgeToEdgeBucket.BROWSER -> edgeToEdgeFeature.browser()
            EdgeToEdgeBucket.SETTINGS -> edgeToEdgeFeature.settings()
        }
        bucketToggle.isEnabled()
    } else {
        false
    }
}
