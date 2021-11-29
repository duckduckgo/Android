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
import com.duckduckgo.app.global.ActivityLifecycleCallbacks
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@ContributesMultibinding(AppObjectGraph::class)
@Singleton
class BrowserApplicationStateInfo @Inject constructor(
    private val observers: Set<@JvmSuppressWildcards BrowserLifecycleObserver>
) : ActivityLifecycleCallbacks {
    private var created = 0
    private var started = 0
    private var resumed = 0

    private val freshLaunchQueue: Queue<Boolean> = LinkedList()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (created++ == 0) freshLaunchQueue.add(true)
    }

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) {
            observers.forEach { it.onOpen(true == freshLaunchQueue.poll()) }
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
        if (started == 0) observers.forEach { it.onClose() }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (created > 0) (--created)
        if (created == 0) observers.forEach { it.onExit() }
    }
}
