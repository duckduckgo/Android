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
import android.content.Context
import android.content.Intent
import android.provider.Browser.EXTRA_APPLICATION_ID
import android.widget.Toast
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

interface AppLinksLauncher {
    fun openAppLink(context: Context?, appLink: AppLink, viewModel: BrowserTabViewModel)
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAppLinksLauncher @Inject constructor() : AppLinksLauncher {

    override fun openAppLink(context: Context?, appLink: AppLink, viewModel: BrowserTabViewModel) {
        if (context == null) return
        appLink.appIntent?.let {
            configureIntent(it, context)
            startActivityOrQuietlyFail(context, it)
        }
        viewModel.clearPreviousUrl()
    }

    private fun configureIntent(
        intent: Intent,
        context: Context,
    ) {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.selector?.addCategory(Intent.CATEGORY_BROWSABLE)

        intent.component = null
        intent.selector?.component = null

        intent.putExtra(EXTRA_APPLICATION_ID, context.packageName)
    }

    private fun startActivityOrQuietlyFail(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            logcat(ERROR) { "Activity not found: ${exception.asLog()}" }
        } catch (exception: SecurityException) {
            showToast(context, R.string.unableToOpenLink)
        }
    }

    private fun showToast(context: Context, @StringRes messageId: Int, length: Int = Toast.LENGTH_LONG) {
        Toast.makeText(context.applicationContext, messageId, length).show()
    }
}
