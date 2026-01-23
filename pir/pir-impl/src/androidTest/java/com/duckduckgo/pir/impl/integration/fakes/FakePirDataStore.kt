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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.pir.impl.store.PirDataStore

/**
 * In-memory implementation of PirDataStore for integration tests.
 */
class FakePirDataStore : PirDataStore {
    override var mainConfigEtag: String? = null
    override var customStatsPixelsLastSentMs: Long = 0L
    override var dauLastSentMs: Long = 0L
    override var wauLastSentMs: Long = 0L
    override var mauLastSentMs: Long = 0L
    override var weeklyStatLastSentMs: Long = 0L
    override var hasBrokerConfigBeenManuallyUpdated: Boolean = false
    override var latestBackgroundScanRunInMs: Long = 0L

    override fun reset() {
        mainConfigEtag = null
        hasBrokerConfigBeenManuallyUpdated = false
        resetUserData()
    }

    override fun resetUserData() {
        customStatsPixelsLastSentMs = 0L
        dauLastSentMs = 0L
        wauLastSentMs = 0L
        mauLastSentMs = 0L
        weeklyStatLastSentMs = 0L
        latestBackgroundScanRunInMs = 0L
    }
}
