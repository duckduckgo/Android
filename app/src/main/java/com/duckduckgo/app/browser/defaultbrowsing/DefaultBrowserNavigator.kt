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

package com.duckduckgo.app.browser.defaultbrowsing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.fragment.app.Fragment
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import timber.log.Timber
import javax.inject.Inject

class DefaultBrowserNavigator @Inject constructor() {

    fun navigateToSettings(
        fragment: Fragment,
        requestCode: Int = 0
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = DefaultBrowserSystemSettings.intent()
            try {
                fragment.startActivityForResult(intent, requestCode)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, fragment.getString(R.string.cannotLaunchDefaultAppSettings))
            }
        }
    }

    fun openDefaultBrowserDialog(
        fragment: Fragment,
        url: String,
        requestCode: Int = 0
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.putExtra(BrowserActivity.LAUNCH_FROM_DEFAULT_BROWSER_DIALOG, true)
        fragment.startActivityForResult(intent, requestCode)
    }
}
