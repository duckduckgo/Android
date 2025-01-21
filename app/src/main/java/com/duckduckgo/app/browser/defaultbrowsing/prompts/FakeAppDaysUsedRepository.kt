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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@SingleInstanceIn(AppScope::class)
class FakeAppDaysUsedRepository @Inject constructor() : AppDaysUsedRepository {

    private val _activeDaysUsedCount = MutableStateFlow(0L)

    fun fakeActiveDaysUsedSinceEnrollment() = _activeDaysUsedCount.asStateFlow()

    fun incrementFakeActiveDaysUsedSinceEnrollment() {
        _activeDaysUsedCount.update {
            it + 1
        }
    }

    override suspend fun getNumberOfDaysAppUsed(): Long {
        TODO("Not yet implemented")
    }

    override suspend fun recordAppUsedToday() {
        TODO("Not yet implemented")
    }

    override suspend fun getNumberOfDaysAppUsedSinceDate(date: Date): Long {
        return _activeDaysUsedCount.value
    }

    override suspend fun getLastActiveDay(): String {
        TODO("Not yet implemented")
    }

    override suspend fun getPreviousActiveDay(): String? {
        TODO("Not yet implemented")
    }
}
