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

package com.duckduckgo.agesignals.impl

import android.app.Activity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.google.android.play.agesignals.AgeSharingConsentWrapperActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ActivityLifecycleCallbacks>())
class AgeSignalsLaunchTrigger @Inject constructor(
    private val feature: AgeSignalsFeature,
    private val checker: AgeSignalsChecker,
    private val store: AgeSignalsStore,
    private val managerProvider: AgeSignalsManagerProvider,
    private val pixels: AgeSignalsPixels,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : ActivityLifecycleCallbacks {

    private val launched = AtomicBoolean(false)

    @Volatile
    private var resumedActivity = WeakReference<Activity>(null)

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)

        if (activity.javaClass.name == CONSENT_ACTIVITY) {
            appCoroutineScope.launch {
                store.recordPromptShown()
                if (!managerProvider.isFakeScenarioActive()) pixels.firePromptShown()
            }
        }

        if (launched.compareAndSet(false, true)) {
            appCoroutineScope.launch(dispatchers.io()) {
                if (!feature.self().isEnabled()) return@launch
                val outcome = checker.check { resumedActivity.get() } ?: return@launch
                if (!managerProvider.isFakeScenarioActive()) pixels.fireCheck(outcome)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity.get() === activity) resumedActivity = WeakReference(null)
    }

    private companion object {
        val CONSENT_ACTIVITY: String = AgeSharingConsentWrapperActivity::class.java.name
    }
}
