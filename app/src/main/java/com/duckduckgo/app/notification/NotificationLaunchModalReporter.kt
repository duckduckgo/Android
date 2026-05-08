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

package com.duckduckgo.app.notification

import android.app.Activity
import android.os.Bundle
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalShownReporter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ActivityLifecycleCallbacks::class,
)
@SingleInstanceIn(AppScope::class)
class NotificationLaunchModalReporter @Inject constructor(
    private val modalShownReporter: ModalShownReporter,
) : ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity.intent?.getBooleanExtra(EXTRA_LAUNCHED_FROM_NOTIFICATION, false) == true) {
            modalShownReporter.reportModalShown()
        }
    }
}
