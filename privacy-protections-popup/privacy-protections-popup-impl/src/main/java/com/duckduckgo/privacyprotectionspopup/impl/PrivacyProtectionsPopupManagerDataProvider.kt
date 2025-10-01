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

import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

interface PrivacyProtectionsPopupManagerDataProvider {
    fun getData(domain: String): Flow<PrivacyProtectionsPopupManagerData>
}

data class PrivacyProtectionsPopupManagerData(
    val protectionsEnabled: Boolean,
    val popupDismissedAt: Instant?,
    val toggleUsedAt: Instant?,
    val popupTriggerCount: Int,
    val doNotShowAgainClicked: Boolean,
    val experimentVariant: PrivacyProtectionsPopupExperimentVariant?,
)

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupManagerDataProviderImpl @Inject constructor(
    private val protectionsStateProvider: ProtectionsStateProvider,
    private val popupDismissDomainRepository: PopupDismissDomainRepository,
    private val dataStore: PrivacyProtectionsPopupDataStore,
) : PrivacyProtectionsPopupManagerDataProvider {

    override fun getData(domain: String): Flow<PrivacyProtectionsPopupManagerData> =
        combine(
            protectionsStateProvider.areProtectionsEnabled(domain),
            popupDismissDomainRepository.getPopupDismissTime(domain),
            dataStore.data,
        ) { protectionsEnabled, popupDismissedAt, popupData ->
            PrivacyProtectionsPopupManagerData(
                protectionsEnabled = protectionsEnabled,
                popupDismissedAt = popupDismissedAt,
                toggleUsedAt = popupData.toggleUsedAt,
                popupTriggerCount = popupData.popupTriggerCount,
                doNotShowAgainClicked = popupData.doNotShowAgainClicked,
                experimentVariant = popupData.experimentVariant,
            )
        }
}
