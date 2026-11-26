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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.util.Locale
import javax.inject.Inject

interface DeviceLanguages {
    /**
     * Language tags, the preferred one first.
     */
    fun get(): List<String>

    /**
     * Name of [languageCode] in the user's language, e.g. "Spanish". Falls back to [languageCode] when unknown.
     */
    fun displayName(languageCode: String): String
}

@ContributesBinding(AppScope::class)
class RealDeviceLanguages @Inject constructor(
    private val context: Context,
) : DeviceLanguages {

    override fun get(): List<String> {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val systemLocales = LocaleManagerCompat.getSystemLocales(context)
        return (appLocales.tags() + systemLocales.tags()).distinct()
    }

    private fun LocaleListCompat.tags(): List<String> = (0 until size()).mapNotNull { get(it)?.toLanguageTag() }

    override fun displayName(languageCode: String): String {
        val display = Locale.getDefault()
        return Locale.forLanguageTag(languageCode).getDisplayLanguage(display)
            .ifBlank { languageCode }
            .replaceFirstChar { it.titlecase(display) }
    }
}
