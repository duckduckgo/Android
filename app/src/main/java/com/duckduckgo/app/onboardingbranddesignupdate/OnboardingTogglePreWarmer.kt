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

package com.duckduckgo.app.onboardingbranddesignupdate

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pre-warms the [OnboardingBrandDesignUpdateToggles.isAppReinstallMutex] toggle into the
 * in-memory cache during app process creation, before the first Activity is created so we don't
 * delay any call to [RealAppBuildConfig.isAppReinstall]
 */
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class OnboardingTogglePreWarmer @Inject constructor(
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            onboardingBrandDesignUpdateToggles.isAppReinstallMutex().isEnabled()
        }
    }
}
