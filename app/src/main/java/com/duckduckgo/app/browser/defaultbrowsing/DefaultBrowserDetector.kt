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

package com.duckduckgo.app.browser.defaultbrowsing

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.duckduckgo.app.browser.BuildConfig
import timber.log.Timber

interface DefaultBrowserDetector {
    fun deviceSupportsDefaultBrowserConfiguration(): Boolean
    fun isDefaultBrowser(): Boolean
    fun hasDefaultBrowser(): Boolean
}

class AndroidDefaultBrowserDetector(private val context: Context) : DefaultBrowserDetector {

    override fun deviceSupportsDefaultBrowserConfiguration(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    override fun isDefaultBrowser(): Boolean {
        val defaultBrowserPackage = defaultBrowserPackage()
        val defaultAlready = defaultBrowserPackage == BuildConfig.APPLICATION_ID
        Timber.i("Default browser identified as $defaultBrowserPackage")
        return defaultAlready
    }

    override fun hasDefaultBrowser(): Boolean = defaultBrowserPackage() != ANDROID_PACKAGE

    private fun defaultBrowserPackage(): String {
        val intent = Intent(ACTION_VIEW, Uri.parse("https://"))
        val resolutionInfo: ResolveInfo? =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolutionInfo?.activityInfo?.packageName ?: ANDROID_PACKAGE
    }

    companion object {
        const val ANDROID_PACKAGE = "android"
    }
}
