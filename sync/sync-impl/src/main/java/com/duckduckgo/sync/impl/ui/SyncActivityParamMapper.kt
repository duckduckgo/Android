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

package com.duckduckgo.sync.impl.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.navigation.api.GlobalActivityStarter.ParamToActivityMapper
import com.duckduckgo.sync.api.SyncActivityFromSetupUrl
import com.duckduckgo.sync.api.SyncActivityWithAnotherDevice
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.impl.SyncFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.sync.impl.ui.v2.SyncActivity as SyncActivityV2

// Typically, ParamToActivityMapper instances are generated in the code via @ContributeToActivityStarter
// annotations on an Activity. This mapper is handwritten because we need to support two separate Activity
// paths as long as the useSimplifiedSync feature flag is available.
//
// Once the FF is removed we can drop one of the code paths and move mapper contribution back to annotations.
@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = ParamToActivityMapper::class)
@ContributesMultibinding(AppScope::class, boundType = MainProcessLifecycleObserver::class)
class SyncActivityParamMapper @Inject constructor(
    // Lazy to break a DI cycle: SyncFeature's generated toggle store transitively depends on
    // GlobalActivityStarter (via VariantManager → ExperimentFiltersManager → Subscriptions),
    // and GlobalActivityStarter injects every ParamToActivityMapper, including this one.
    private val syncFeature: Lazy<SyncFeature>,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ParamToActivityMapper, MainProcessLifecycleObserver {

    @Volatile private var useSimplifiedSync = false

    override fun map(activityParams: ActivityParams): Class<out AppCompatActivity>? {
        return when (activityParams) {
            is SyncActivityWithEmptyParams,
            is SyncActivityWithSourceParams,
            is SyncActivityFromSetupUrl,
            is SyncActivityWithAnotherDevice,
            -> if (useSimplifiedSync) {
                SyncActivityV2::class.java
            } else {
                SyncActivity::class.java
            }

            else -> null
        }
    }

    override fun map(deeplinkActivityParams: DeeplinkActivityParams): ActivityParams? = null

    override fun onCreate(owner: LifecycleOwner) {
        // We don't read useSimplifiedSync().isEnabled() directly when mapping Activity params
        // because getting a feature flag value can perform an I/O disk read. This way
        // we avoid potential ANRs.
        appScope.launch {
            syncFeature.get().useSimplifiedSync()
                .enabled()
                .collect { useSimplifiedSync = it }
        }
    }
}
