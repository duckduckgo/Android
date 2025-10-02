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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupData
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakePrivacyProtectionsPopupDataStore : PrivacyProtectionsPopupDataStore {

    override val data = MutableStateFlow(
        PrivacyProtectionsPopupData(
            toggleUsedAt = null,
            popupTriggerCount = 0,
            doNotShowAgainClicked = false,
            experimentVariant = null,
        ),
    )

    override suspend fun getToggleUsageTimestamp(): Instant? =
        data.first().toggleUsedAt

    override suspend fun setToggleUsageTimestamp(timestamp: Instant) {
        data.update { it.copy(toggleUsedAt = timestamp) }
    }

    override suspend fun getPopupTriggerCount(): Int =
        data.first().popupTriggerCount

    override suspend fun setPopupTriggerCount(count: Int) {
        data.update { it.copy(popupTriggerCount = count) }
    }

    override suspend fun getDoNotShowAgainClicked(): Boolean =
        data.first().doNotShowAgainClicked

    override suspend fun setDoNotShowAgainClicked(clicked: Boolean) {
        data.update { it.copy(doNotShowAgainClicked = clicked) }
    }

    override suspend fun getExperimentVariant(): PrivacyProtectionsPopupExperimentVariant? =
        data.first().experimentVariant

    override suspend fun setExperimentVariant(variant: PrivacyProtectionsPopupExperimentVariant) {
        data.update { it.copy(experimentVariant = variant) }
    }
}
