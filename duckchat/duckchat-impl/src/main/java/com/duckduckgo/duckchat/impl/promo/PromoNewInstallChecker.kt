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

package com.duckduckgo.duckchat.impl.promo

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface PromoNewInstallChecker {
    /**
     * @return whether the app was installed on this device for the first time, at most [installWindowDays] whole days ago.
     * Always false below Android 11, where re-installs can't be told apart from first installs.
     */
    suspend fun isNewInstall(installWindowDays: Int): Boolean
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealPromoNewInstallChecker @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) : PromoNewInstallChecker {
    private var cachedFirstInstallTimeMillis: Long? = null

    override suspend fun isNewInstall(installWindowDays: Int): Boolean {
        if (appBuildConfig.sdkInt < MIN_SDK_DETECTING_RE_INSTALLS) {
            // Exclude installs below Android 11 since we can't tell re-installs apart from first-time installs
            return false
        }
        if (appBuildConfig.isAppReinstall()) {
            return false
        }
        val installAge = (currentTimeProvider.currentTimeMillis() - firstInstallTimeMillis()).milliseconds
        return installAge.inWholeDays in 0L..installWindowDays
    }

    // Unlike the app's install store, the package's install time isn't reset by clearing app data,
    // nor delayed until the app is first opened. It's only set on installation.
    private suspend fun firstInstallTimeMillis(): Long = cachedFirstInstallTimeMillis
        ?: withContext(dispatcherProvider.io()) {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.also { cachedFirstInstallTimeMillis = it }

    private companion object {
        const val MIN_SDK_DETECTING_RE_INSTALLS = 30 // Android 11
    }
}
