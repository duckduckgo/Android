/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface AppLinksLauncher {
    fun openAppLink(context: Context?, appLink: AppLink, viewModel: BrowserTabViewModel)
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAppLinksLauncher @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AppLinksLauncher {

    @Suppress("NewApi")
    override fun openAppLink(context: Context?, appLink: AppLink, viewModel: BrowserTabViewModel) {
        if (context == null) return
        appLink.appIntent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityOrQuietlyFail(context, it)
        } ?: run {
            if (appLink.excludedComponents != null && appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
                val title = context.getString(R.string.appLinkIntentChooserTitle)
                val chooserIntent = getChooserIntent(appLink.uriString, title, appLink.excludedComponents!!)
                startActivityOrQuietlyFail(context, chooserIntent)
            }
        }
        viewModel.clearPreviousUrl()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getChooserIntent(url: String?, title: String, excludedComponents: List<ComponentName>): Intent {
        val urlIntent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return Intent.createChooser(urlIntent, title).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
        }
    }

    private fun startActivityOrQuietlyFail(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Timber.e(exception, "Activity not found")
        } catch (exception: SecurityException) {
            showToast(context, R.string.unableToOpenLink)
        }
    }

    private fun showToast(context: Context, @StringRes messageId: Int, length: Int = Toast.LENGTH_LONG) {
        Toast.makeText(context.applicationContext, messageId, length).show()
    }
}
