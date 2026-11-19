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

package com.duckduckgo.remote.messaging.impl.matchers

import android.content.Context
import android.os.Build
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

internal const val SEM_FEATURE = "com.samsung.feature.samsung_experience_mobile"
internal const val SEM_FEATURE_LITE = "com.samsung.feature.samsung_experience_mobile_lite"
internal const val SEM_PLATFORM_INT_FIELD = "SEM_PLATFORM_INT"
internal const val ONE_UI_BASE = 90000

data class OneUiVersion(val major: Int, val minor: Int)

interface OneUiVersionProvider {
    fun get(): OneUiVersion?
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealOneUiVersionProvider @Inject constructor(
    private val context: Context,
) : OneUiVersionProvider {

    private val version: OneUiVersion? by lazy { detect() }

    override fun get(): OneUiVersion? = version

    private fun detect(): OneUiVersion? {
        return runCatching {
            val packageManager = context.packageManager
            if (!packageManager.hasSystemFeature(SEM_FEATURE) && !packageManager.hasSystemFeature(SEM_FEATURE_LITE)) {
                return@runCatching null
            }
            val raw = Build.VERSION::class.java.getDeclaredField(SEM_PLATFORM_INT_FIELD).getInt(null)
            decodeSemPlatformInt(raw)
        }.onFailure {
            logcat(INFO) { "RMF: One UI version unavailable: ${it.javaClass.simpleName}" }
        }.getOrNull()
    }
}

internal fun decodeSemPlatformInt(raw: Int): OneUiVersion? {
    val version = raw - ONE_UI_BASE
    val major = version / 10000
    if (major < 1) return null
    val minor = (version % 10000) / 100
    return OneUiVersion(major = major, minor = minor)
}
