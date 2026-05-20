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
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ActivityLifecycleCallbacks::class,
)
@SingleInstanceIn(AppScope::class)
class NotificationLaunchModalReporter @Inject constructor(
    private val modalShownReporter: ModalShownReporter,
) : ActivityLifecycleCallbacks {

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (savedInstanceState != null) return

        // This callback runs for every activity in the process, including ones launched from external apps
        // (e.g. IntentDispatcherActivity receiving a share intent). Reading any extra triggers full Bundle
        // unparcelling, which throws when the foreign intent carries a Parcelable whose class is absent
        // from our classpath, so we guard the read here even though our own notification intents are safe.
        val launchedFromNotification = try {
            activity.intent?.getBooleanExtra(EXTRA_LAUNCHED_FROM_NOTIFICATION, false) == true
        } catch (e: RuntimeException) {
            logcat(ERROR) { "Failed to read launched-from-notification extra: ${e.asLog()}" }
            false
        }
        if (launchedFromNotification) {
            modalShownReporter.reportModalShown()
        }
    }
}
