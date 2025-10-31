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

package com.duckduckgo.autofill.impl.importing.takeout.webflow.journey

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface ImportGoogleBookmarksDurationBucketing {
    fun bucket(durationMillis: Long): String
}

@ContributesBinding(AppScope::class)
class RealImportGoogleBookmarksDurationBucketing @Inject constructor() : ImportGoogleBookmarksDurationBucketing {

    override fun bucket(durationMillis: Long): String {
        if (durationMillis < 0) return "negative"
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)

        return when (seconds) {
            in 0..19 -> "20"
            in 20..39 -> "40"
            in 40..59 -> "60"
            in 60..89 -> "90"
            in 90..119 -> "120"
            in 120..149 -> "150"
            in 150..179 -> "180"
            in 180..239 -> "240"
            in 240..299 -> "300"
            else -> "longer"
        }
    }
}
