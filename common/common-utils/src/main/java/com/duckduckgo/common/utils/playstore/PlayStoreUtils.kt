/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.common.utils.playstore

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

interface PlayStoreUtils {

    fun installedFromPlayStore(): Boolean
    fun launchPlayStore()
    fun launchPlayStore(appPackage: String)
    fun isPlayStoreInstalled(): Boolean
}

@ContributesBinding(AppScope::class)
class PlayStoreAndroidUtils @Inject constructor(val context: Context) : PlayStoreUtils {

    override fun installedFromPlayStore(): Boolean {
        return try {
            val installSource = context.packageManager.getInstallerPackageName(DDG_APP_PACKAGE)
            return matchesPlayStoreInstallSource(installSource)
        } catch (e: IllegalArgumentException) {
            logcat(WARN) { "Can't determine if app was installed from Play Store; assuming it wasn't" }
            false
        }
    }

    private fun matchesPlayStoreInstallSource(installSource: String?): Boolean {
        logcat(INFO) { "DuckDuckGo app install source detected: $installSource" }
        return installSource == PLAY_STORE_PACKAGE
    }

    override fun isPlayStoreInstalled(): Boolean {
        return try {
            if (!isPlayStoreActivityResolvable(context)) {
                logcat(INFO) { "Cannot resolve Play Store activity" }
                return false
            }

            val isAppEnabled = isPlayStoreAppEnabled()
            logcat(INFO) { "The Play Store app is installed ${if (isAppEnabled) "and enabled" else "but disabled"}" }
            return isAppEnabled
        } catch (e: PackageManager.NameNotFoundException) {
            logcat(INFO) { "Could not find package details for $PLAY_STORE_PACKAGE; Play Store is not installed" }
            false
        }
    }

    private fun isPlayStoreAppEnabled(): Boolean {
        context.packageManager.getPackageInfo(PLAY_STORE_PACKAGE, 0)
        val appInfo = context.packageManager.getApplicationInfo(PLAY_STORE_PACKAGE, 0)
        return appInfo.enabled
    }

    private fun isPlayStoreActivityResolvable(context: Context): Boolean {
        return playStoreIntent().resolveActivity(context.packageManager) != null
    }

    private fun playStoreIntent(appPackage: String = DDG_APP_PACKAGE): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("$PLAY_STORE_URI$appPackage")
            setPackage(PLAY_STORE_PACKAGE)
        }
    }

    override fun launchPlayStore() {
        val intent = playStoreIntent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logcat(ERROR) { "Could not launch the Play Store: ${e.asLog()}" }
        }
    }

    override fun launchPlayStore(appPackage: String) {
        val intent = playStoreIntent(appPackage)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            logcat(ERROR) { "Could not launch the Play Store: ${e.asLog()}" }
        }
    }

    companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val PLAY_STORE_REFERRAL_SERVICE = "com.google.android.finsky.externalreferrer.GetInstallReferrerService"
        private const val PLAY_STORE_URI = "https://play.google.com/store/apps/details?id="
        private const val DDG_APP_PACKAGE = "com.duckduckgo.mobile.android"
    }
}
