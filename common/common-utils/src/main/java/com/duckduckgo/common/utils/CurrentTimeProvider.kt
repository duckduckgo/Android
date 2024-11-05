/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.os.SystemClock
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDateTime
import javax.inject.Inject

interface CurrentTimeProvider {
    fun elapsedRealtime(): Long

    fun currentTimeMillis(): Long

    fun localDateTimeNow(): LocalDateTime
}

@ContributesBinding(AppScope::class)
class RealCurrentTimeProvider @Inject constructor() : CurrentTimeProvider {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
}
