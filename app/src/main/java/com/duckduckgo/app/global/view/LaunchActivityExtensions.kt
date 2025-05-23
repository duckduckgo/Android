/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import logcat.LogPriority.WARN
import logcat.logcat

fun FragmentActivity.launchExternalActivity(intent: Intent) {
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Toast.makeText(this, R.string.no_compatible_third_party_app_installed, Toast.LENGTH_SHORT).show()
    }
}

fun Context.launchDefaultAppActivity() {
    try {
        val intent = DefaultBrowserSystemSettings.intent()
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
        logcat(WARN) { errorMessage }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
}
