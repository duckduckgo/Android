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

package com.duckduckgo.app.global

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import logcat.LogPriority.INFO
import logcat.logcat

class RealDefaultRoleBrowserDialog(
    private val appInstallStore: AppInstallStore,
    private val appBuildConfig: AppBuildConfig,
) : DefaultRoleBrowserDialog {

    /**
     * @return an Intent to launch the role browser dialog
     */
    @Suppress("NewApi") // we use appBuildConfig
    override fun createIntent(context: Context): Intent? {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null

            val isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)
            if (isRoleAvailable) {
                val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                return if (!isRoleHeld) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                } else {
                    logcat(INFO) { "Browser role held" }
                    null
                }
            }
        }
        return null
    }

    override fun shouldShowDialog(): Boolean {
        // The second and subsequent times the dialog is shown, the system allows the user to click on "don't show again"
        // we will get the same result as if the dialog was just dismissed.
        return appBuildConfig.sdkInt >= Build.VERSION_CODES.Q &&
            appInstallStore.newDefaultBrowserDialogCount < DEFAULT_BROWSER_DIALOG_MAX_ATTEMPTS
    }

    override fun dialogShown() {
        appInstallStore.newDefaultBrowserDialogCount++
    }

    companion object {
        private const val DEFAULT_BROWSER_DIALOG_MAX_ATTEMPTS = 2
    }
}
