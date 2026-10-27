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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PasswordsImportSyncPromotion {
    suspend fun canShowPromotion(importedPasswords: Int): Boolean
    fun onPromotionDisplayed()
    fun onPromotionConfirmed()
}

@ContributesBinding(AppScope::class, binding = binding<PasswordsImportSyncPromotion>())
class RealPasswordsImportSyncPromotion @Inject constructor(
    private val syncState: DeviceSyncState,
    private val syncPromotionFeature: SyncPromotionFeature,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : PasswordsImportSyncPromotion {
    override suspend fun canShowPromotion(importedPasswords: Int): Boolean {
        if (importedPasswords == 0) return false

        return coroutineScope {
            val isEnabled = async { isPromotionEnabled() }
            val isSyncAvailable = async { canTurnOnSync() }
            isEnabled.await() && isSyncAvailable.await()
        }
    }

    override fun onPromotionDisplayed() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParams())
    }

    override fun onPromotionConfirmed() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParams())
    }

    private suspend fun isPromotionEnabled(): Boolean = withContext(dispatchers.io()) {
        syncPromotionFeature.self().isEnabled() && syncPromotionFeature.passwordsImport().isEnabled()
    }

    private suspend fun canTurnOnSync(): Boolean = withContext(dispatchers.io()) {
        syncState.isFeatureEnabled() && !syncState.isUserSignedInOnDevice()
    }

    private fun pixelParams() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to "passwords_import")
}
