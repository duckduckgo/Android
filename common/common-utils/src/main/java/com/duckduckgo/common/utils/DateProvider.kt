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

package com.duckduckgo.common.utils

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Provider for getting the current UTC ISO local date string (YYYY-MM-dd format).
 */
interface DateProvider {
    /**
     * Returns the current UTC ISO local date string in YYYY-MM-dd format.
     */
    fun getUtcIsoLocalDate(): String
}

@ContributesBinding(AppScope::class)
class RealDateProvider @Inject constructor() : DateProvider {
    override fun getUtcIsoLocalDate(): String {
        // returns YYYY-MM-dd
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
