/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import javax.inject.Inject

/**
 * Dev-only benchmark configuration. Reads a plain file written via `adb run-as` on
 * debuggable internal builds. Always returns null on production builds.
 */
interface PirBenchmarkConfig {
    /**
     * Returns the developer-set override for the maximum detached WebView count, or null
     * when not on an internal build, the file is absent, blank, or not an integer.
     * Callers are responsible for coercing the value into the valid range.
     */
    fun getWebViewCountOverride(): Int?
}

@ContributesBinding(AppScope::class)
class RealPirBenchmarkConfig @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
) : PirBenchmarkConfig {

    override fun getWebViewCountOverride(): Int? {
        if (!appBuildConfig.isInternalBuild()) return null
        return runCatching {
            val file = File(context.filesDir, OVERRIDE_FILE_NAME)
            if (!file.exists()) return null
            file.readText().trim().toIntOrNull()
        }.getOrNull()
    }

    companion object {
        const val OVERRIDE_FILE_NAME = "pir_benchmark_webview_count"
    }
}
