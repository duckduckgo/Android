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

package com.duckduckgo.pagetranslation.impl.language

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

fun interface DeviceLanguages {
    /**
     * Language tags, the preferred one first.
     */
    fun get(): List<String>
}

@ContributesBinding(AppScope::class)
class RealDeviceLanguages @Inject constructor(
    private val context: Context,
) : DeviceLanguages {

    override fun get(): List<String> {
        val locales = context.resources.configuration.locales
        return (0 until locales.size()).map { locales[it].toLanguageTag() }
    }
}
