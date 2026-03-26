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

package com.duckduckgo.autofill.impl.deviceauth

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class AutofillGracePeriodLifecycleObserver @Inject constructor(
    private val gracePeriod: AutofillAuthorizationGracePeriod,
) : MainProcessLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        logcat { "App in background, invalidating autofill grace period" }
        gracePeriod.invalidate()
    }
}
