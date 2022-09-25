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

package com.duckduckgo.remote.messaging.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.LocalDateTime

class LocalRemoteMessagingConfigRepositoryTest {

    @Test
    fun whenRemoteConfigTimestampGreaterThan1DayThenConfigExpired() {
        val remoteMessagingConfig = RemoteMessagingConfig(
            version = 0,
            evaluationTimestamp = databaseTimestampFormatter().format(LocalDateTime.now().minusDays(2L))
        )

        val expired = remoteMessagingConfig.expired()

        assertTrue(expired)
    }

    @Test
    fun whenRemoteConfigTimestampLessThan1DayThenConfigIsNotExpired() {
        val remoteMessagingConfig = RemoteMessagingConfig(
            version = 0,
            evaluationTimestamp = databaseTimestampFormatter().format(LocalDateTime.now().minusHours(15L))
        )

        val expired = remoteMessagingConfig.expired()

        assertFalse(expired)
    }
}
