/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.installation.impl.installer

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface InstallSourceExtractor {

    /**
     * Extracts the installer package name from the PackageManager.
     */
    fun extract(): String?
}

@ContributesBinding(AppScope::class)
class RealInstallSourceExtractor @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : InstallSourceExtractor {

    @SuppressLint("NewApi")
    override fun extract(): String? {
        return if (appBuildConfig.sdkInt >= 30) {
            installationSourceModern(context.packageName)
        } else {
            installationSourceLegacy(context.packageName)
        }
    }

    @Suppress("DEPRECATION")
    private fun installationSourceLegacy(packageName: String): String? {
        return context.packageManager.getInstallerPackageName(packageName)
    }

    @RequiresApi(30)
    private fun installationSourceModern(packageName: String): String? {
        return context.packageManager.getInstallSourceInfo(packageName).installingPackageName
    }
}
