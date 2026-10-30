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

package com.duckduckgo.macrobenchmark

import android.app.Instrumentation
import android.content.Intent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.duckduckgo.testseeder.api.TestSeederKey

internal const val TARGET_PACKAGE = "com.duckduckgo.mobile.android"

internal val BROWSER_TAB = By.res(TARGET_PACKAGE, "newTabRootLayout")

internal const val SCREEN_TIMEOUT_MS = 30_000L

/**
 * Seeding only runs from an intent that creates the launch activity.
 */
internal fun seededLaunchIntent(instrumentation: Instrumentation): Intent =
    checkNotNull(instrumentation.context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(TestSeederKey.IS_MACROBENCHMARK.key, "true")
        .putExtra(TestSeederKey.SKIP_ONBOARDING.key, "true")

/**
 * Wait browser tab is displayed on the screen.
 */
internal fun awaitBrowserTab(instrumentation: Instrumentation) {
    check(UiDevice.getInstance(instrumentation).wait(Until.hasObject(BROWSER_TAB), SCREEN_TIMEOUT_MS)) {
        "Browser tab was not shown after onboarding"
    }
}

/**
 * Complete the onboarding flow.
 */
internal fun completeOnboarding(instrumentation: Instrumentation) {
    instrumentation.context.startActivity(seededLaunchIntent(instrumentation))
    awaitBrowserTab(instrumentation)
}
