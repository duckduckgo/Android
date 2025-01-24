/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service.mapper

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AppFingerprintProvider {
    fun getSHA256HexadecimalFingerprint(packageName: String): List<String>
}

@ContributesBinding(AppScope::class)
class RealAppFingerprintProvider @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val context: Context,
) : AppFingerprintProvider {
    override fun getSHA256HexadecimalFingerprint(packageName: String): List<String> =
        context.packageManager.getSHA256HexadecimalFingerprintCompat(packageName, appBuildConfig)
}
