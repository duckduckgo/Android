/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.attributed.metrics.store

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface DateProvider {
    fun getCurrentDate(): String

    fun getDateMinusDays(days: Int): String
}

@ContributesBinding(AppScope::class)
class RealDateProvider @Inject constructor() : DateProvider {
    override fun getCurrentDate(): String = LocalDate.now().format(DATE_FORMATTER)

    override fun getDateMinusDays(days: Int): String = LocalDate.now().minusDays(days.toLong()).format(DATE_FORMATTER)

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
