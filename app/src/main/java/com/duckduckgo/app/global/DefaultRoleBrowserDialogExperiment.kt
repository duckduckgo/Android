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
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.duckduckgo.app.statistics.VariantManager
import timber.log.Timber
import javax.inject.Inject

class DefaultRoleBrowserDialogExperiment @Inject constructor(
    private val appContext: Context,
    private val variantManager: VariantManager
) {

    private val preferences: SharedPreferences
        get() = appContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    private var dialogCount: Int
        get() = preferences.getInt(ROLE_MANAGER_DIALOG_KEY, 0)
        set(value) = preferences.edit { putInt(ROLE_MANAGER_DIALOG_KEY, value) }

    /**
     * @return an Intent to launch the role browser dialog
     */
    fun createIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null

            val isRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)
            if (isRoleAvailable) {
                val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                return if (!isRoleHeld) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                } else {
                    Timber.i("Browser role held")
                    null
                }
            }
        }
        return null
    }

    fun shouldShowExperiment(): Boolean {
        return variantManager.getVariant().hasFeature(VariantManager.VariantFeature.SetDefaultBrowserDialog) && dialogCount < 2
    }

    fun experimentShown() {
        dialogCount++
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.role.browser.dialog"
        private const val ROLE_MANAGER_DIALOG_KEY = "ROLE_MANAGER_DIALOG_KEY"
    }
}
