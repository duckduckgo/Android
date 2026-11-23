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
package com.duckduckgo.app.quack

import android.app.Activity
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
class ResumedActivityTracker @Inject constructor() : ActivityLifecycleCallbacks {
    @Volatile
    var resumedActivity: String? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = activity.javaClass.simpleName
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity == activity.javaClass.simpleName) resumedActivity = null
    }
}
