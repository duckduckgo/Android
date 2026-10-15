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

package com.duckduckgo.app.browser.applinks

import android.content.Intent
import android.content.pm.ResolveInfo

data class ExternalAppLaunch(
    val intent: Intent,
    val activities: List<ResolveInfo>,
    val useChooser: Boolean,
    val showConfirmation: Boolean,
    val finishCustomTab: Boolean,
)

/**
 * Decides how an external app intent is launched. A [trustedCallerIntent] replaces [intent] and skips the confirmation
 * prompt; the custom tab is only finished when the caller is opened directly, since a dismissed chooser launches nothing.
 */
fun planExternalAppLaunch(
    intent: Intent,
    activities: List<ResolveInfo>,
    useFirstActivityFound: Boolean,
    showConfirmation: Boolean,
    trustedCallerIntent: TrustedCallerIntent?,
    finishCustomTabOnTrustedCallerLaunch: Boolean,
): ExternalAppLaunch {
    val launchActivities = trustedCallerIntent?.activities ?: activities
    val useChooser = launchActivities.size != 1 && !useFirstActivityFound
    return ExternalAppLaunch(
        intent = trustedCallerIntent?.intent ?: intent,
        activities = launchActivities,
        useChooser = useChooser,
        showConfirmation = showConfirmation && trustedCallerIntent == null,
        finishCustomTab = trustedCallerIntent != null && finishCustomTabOnTrustedCallerLaunch && !useChooser,
    )
}
