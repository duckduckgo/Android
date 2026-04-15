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

package com.duckduckgo.app.anr.internal.setting

import android.content.Context
import com.duckduckgo.android_crashkit.Crashpad
import com.duckduckgo.android_crashkit.CrashpadConfig
import com.duckduckgo.app.anr.ndk.CrashpadInitializer
import com.duckduckgo.app.anr.ndk.DefaultCrashpadInitializer
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class, replaces = [DefaultCrashpadInitializer::class])
class InternalCrashpadInitializer @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val uploadConfig: CrashpadUploadConfig,
) : CrashpadInitializer {

    override fun initialize(
        extraAnnotations: Map<String, String>,
        dynamicAnnotationKeys: Set<String>,
        onCrash: (() -> Unit)?,
    ): Boolean = Crashpad.init(
        context,
        platform = "Android",
        version = "${appBuildConfig.versionName}-${appBuildConfig.flavor}",
        osVersion = "Android SDK ${appBuildConfig.sdkInt}",
        extraAnnotations = extraAnnotations,
        dynamicAnnotationKeys = dynamicAnnotationKeys,
        config = CrashpadConfig(
            uploadUrl = uploadConfig.uploadUrl,
            uploadsEnabled = uploadConfig.uploadUrl.isNotEmpty(),
            noRateLimit = uploadConfig.noRateLimit,
            onCrash = onCrash,
        ),
    )
}
