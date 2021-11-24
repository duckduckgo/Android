/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.job

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.notification.AndroidNotificationScheduler
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import dagger.SingleIn

@ContributesMultibinding(AppObjectGraph::class)
@SingleIn(AppObjectGraph::class)
class AndroidWorkScheduler @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val notificationScheduler: AndroidNotificationScheduler,
    private val jobCleaner: JobCleaner
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun scheduleWork() {
        Timber.v("Scheduling work")
        appCoroutineScope.launch {
            jobCleaner.cleanDeprecatedJobs()
            notificationScheduler.scheduleNextNotification()
        }
    }
}
