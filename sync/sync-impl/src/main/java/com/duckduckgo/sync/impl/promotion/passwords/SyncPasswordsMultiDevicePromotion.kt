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

package com.duckduckgo.sync.impl.promotion.passwords

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.promotion.SyncPromotionAppInstall
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

interface SyncPasswordsMultiDevicePromotion {
    suspend fun canShowPromotion(savedPasswords: Int): Boolean
}

@ContributesBinding(AppScope::class, binding = binding<SyncPasswordsMultiDevicePromotion>())
class RealSyncPasswordsMultiDevicePromotion @Inject constructor(
    private val syncState: DeviceSyncState,
    private val syncPromotionFeature: SyncPromotionFeature,
    private val syncFeature: SyncFeature,
    private val promotionDataStore: SyncPromotionDataStore,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val dispatchers: DispatcherProvider,
    private val appInstall: SyncPromotionAppInstall,
) : SyncPasswordsMultiDevicePromotion {

    override suspend fun canShowPromotion(savedPasswords: Int): Boolean {
        if (savedPasswords == 0) return false

        return coroutineScope {
            val preconditions = listOf(
                async { isPromotionEnabled() },
                async { isSyncingWithSingleDevice() },
                async { !isPromotionDismissed() },
                async { hasWaitedLongEnough() },
            ).awaitAll()
            preconditions.all { it }
        }
    }

    private suspend fun isPromotionEnabled(): Boolean = withContext(dispatchers.io()) {
        syncPromotionFeature.self().isEnabled() &&
            syncPromotionFeature.passwords().isEnabled() &&
            syncPromotionFeature.leveragePromosRedesign().isEnabled() &&
            // Without the refresh on every sync, the cached device count can miss devices that joined since, so we can't trust it here.
            syncFeature.refreshConnectedDevicesOnSync().isEnabled()
    }

    private suspend fun isSyncingWithSingleDevice(): Boolean {
        val isSignedIn = withContext(dispatchers.io()) {
            syncState.isFeatureEnabled() && syncState.isUserSignedInOnDevice()
        }
        return isSignedIn && connectedDevicesRepository.latestConnectedDeviceCount() == 1
    }

    private suspend fun isPromotionDismissed(): Boolean = promotionDataStore.hasPromoBeenDismissed(PasswordsScreenMultiDevice)

    private suspend fun hasWaitedLongEnough(): Boolean {
        val installAge = appInstall.getInstallAge() ?: return false
        if (installAge <= MIN_INSTALL_DURATION) return false

        val timeSinceSetUpPromotionShown = promotionDataStore.getTimeSinceLastPromoImpression(PasswordsScreen) ?: return true
        return timeSinceSetUpPromotionShown >= SET_UP_PROMOTION_COOLDOWN
    }

    private companion object {
        val MIN_INSTALL_DURATION = 7.days
        val SET_UP_PROMOTION_COOLDOWN = 24.hours
    }
}
