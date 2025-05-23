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

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.di.scopes.AppScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

interface AppLinksSnackBarConfigurator {
    fun configureAppLinkSnackBar(view: View?, appLink: AppLink, viewModel: BrowserTabViewModel): Snackbar?
}

@ContributesBinding(AppScope::class)
class DuckDuckGoAppLinksSnackBarConfigurator @Inject constructor(
    private val appLinksLauncher: AppLinksLauncher,
    private val pixel: Pixel,
) : AppLinksSnackBarConfigurator {

    override fun configureAppLinkSnackBar(view: View?, appLink: AppLink, viewModel: BrowserTabViewModel): Snackbar? {
        return view?.let {
            val context = it.context
            val (message, action) = getSnackBarContent(context, appLink) ?: return null

            it.makeSnackbarWithNoBottomInset(message, Snackbar.LENGTH_LONG).apply {
                setAction(action) {
                    pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_OPEN_ACTION_PRESSED)
                    appLinksLauncher.openAppLink(context, appLink, viewModel)
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onShown(transientBottomBar: Snackbar?) {
                            super.onShown(transientBottomBar)
                            pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_SHOWN)
                        }
                    },
                )
                duration = DURATION
            }
        }
    }

    private fun getSnackBarContent(context: Context, appLink: AppLink): Pair<String, String>? {
        val appIntent = appLink.appIntent
        return if (appIntent != null) {
            val packageName = appIntent.component?.packageName ?: return null
            val message = context.getString(R.string.appLinkSnackBarMessage, getAppName(context, packageName))
            val action = context.getString(R.string.appLinkSnackBarAction)
            message to action
        } else {
            val message = context.getString(R.string.appLinkMultipleSnackBarMessage)
            val action = context.getString(R.string.appLinkMultipleSnackBarAction)
            message to action
        }
    }

    private fun getAppName(context: Context, packageName: String): String? {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (exception: PackageManager.NameNotFoundException) {
            logcat(ERROR) { "App name not found: ${exception.asLog()}" }
            null
        }
    }

    companion object {
        const val DURATION = 6000
    }
}
