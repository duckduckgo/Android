/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.buildconfig

import android.os.Build
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.lang.IllegalStateException
import java.util.*
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealAppBuildConfig @Inject constructor() : AppBuildConfig {
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val buildType: String = BuildConfig.BUILD_TYPE
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val versionName: String = BuildConfig.VERSION_NAME
    override val flavor: BuildFlavor
        get() = when (BuildConfig.FLAVOR) {
            "internal" -> BuildFlavor.INTERNAL
            "fdroid" -> BuildFlavor.FDROID
            "play" -> BuildFlavor.PLAY
            else -> throw IllegalStateException("Unknown app flavor")
        }
    override val sdkInt: Int = Build.VERSION.SDK_INT
    override val manufacturer: String = Build.MANUFACTURER
    override val model: String = Build.MODEL
    override val isTest by lazy {
        try {
            Class.forName("org.junit.Test")
            true
        } catch (e: Exception) {
            false
        }
    }
    override val deviceLocale: Locale
        get() = Locale.getDefault()
}
