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

import com.duckduckgo.common.utils.CurrentTimeProvider
import java.time.LocalDateTime

class FakeCurrentTimeProvider(var nowMillis: Long) : CurrentTimeProvider {
    override fun currentTimeMillis(): Long = nowMillis

    override fun elapsedRealtime(): Long = throw UnsupportedOperationException()

    override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
}
