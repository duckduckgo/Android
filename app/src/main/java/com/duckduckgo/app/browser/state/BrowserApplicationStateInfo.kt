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

package com.duckduckgo.app.browser.state

import android.app.Activity
import android.os.Bundle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class BrowserApplicationStateInfo @Inject constructor(
    private val observers: DaggerSet<BrowserLifecycleObserver>,
) : ActivityLifecycleCallbacks {
    private var created = 0
    private var started = 0
    private var resumed = 0

    private var isFreshLaunch: Boolean = false
    private var overrideIsFreshLaunch: Boolean = false

    // True when the started-count hit 0 due to a config-change recreate (rotation / Activity.recreate()),
    // not a real background->foreground; suppresses onClose()/onOpen() so the fg pipeline doesn't run on it.
    private var stoppedForRecreate = false

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (created++ == 0 && !overrideIsFreshLaunch) isFreshLaunch = true
    }

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) {
            if (stoppedForRecreate) {
                // The recreated instance came back; this is not a real foreground.
                stoppedForRecreate = false
            } else {
                observers.forEach { it.onOpen(isFreshLaunch) }
                isFreshLaunch = false
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        (++resumed)
        observers.forEach { it.onForeground() }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed > 0) (--resumed)
        observers.forEach { it.onBackground() }
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) (--started)
        if (started == 0) {
            if (activity.isChangingConfigurations) {
                // recreate()/rotation, not a real background
                stoppedForRecreate = true
            } else {
                observers.forEach { it.onClose() }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (created > 0) (--created)
        if (created == 0 && (activity is BrowserActivity)) {
            if (activity.destroyedByBackPress || activity.isChangingConfigurations) {
                overrideIsFreshLaunch = true
            } else {
                observers.forEach { it.onExit() }
            }
        }
    }
}
