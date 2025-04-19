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

package com.duckduckgo.verifiedinstallation.installsource

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface InstallSourceExtractor {
    fun extract(): String?
}

@ContributesBinding(AppScope::class)
class InstallSourceExtractorImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
) : InstallSourceExtractor {

    @SuppressLint("NewApi")
    override fun extract(): String? {
        return kotlin.runCatching {
            if (appBuildConfig.sdkInt >= VERSION_CODES.R) {
                installationSourceModern(context.packageName)
            } else {
                installationSourceLegacy(context.packageName)
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun installationSourceLegacy(packageName: String): String? {
        return context.packageManager.getInstallerPackageName(packageName)
    }

    @RequiresApi(VERSION_CODES.R)
    private fun installationSourceModern(packageName: String): String? {
        return context.packageManager.getInstallSourceInfo(packageName).installingPackageName
    }
}
