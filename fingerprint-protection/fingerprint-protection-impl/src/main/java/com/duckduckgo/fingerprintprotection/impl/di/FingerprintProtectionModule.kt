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

package com.duckduckgo.fingerprintprotection.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.fingerprintprotection.store.ALL_MIGRATIONS
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingbattery.FingerprintingBatteryRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingbattery.RealFingerprintingBatteryRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingcanvas.FingerprintingCanvasRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingcanvas.RealFingerprintingCanvasRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintinghardware.FingerprintingHardwareRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintinghardware.RealFingerprintingHardwareRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize.FingerprintingScreenSizeRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize.RealFingerprintingScreenSizeRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingtemporarystorage.FingerprintingTemporaryStorageRepository
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingtemporarystorage.RealFingerprintingTemporaryStorageRepository
import com.duckduckgo.fingerprintprotection.store.seed.FingerprintProtectionSeedRepository
import com.duckduckgo.fingerprintprotection.store.seed.RealFingerprintProtectionSeedRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object FingerprintProtectionModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintProtectionDatabase(context: Context): FingerprintProtectionDatabase {
        return Room.databaseBuilder(context, FingerprintProtectionDatabase::class.java, "fingerprint_protection.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintingBatteryRepository(
        database: FingerprintProtectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): FingerprintingBatteryRepository {
        return RealFingerprintingBatteryRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintingCanvasRepository(
        database: FingerprintProtectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): FingerprintingCanvasRepository {
        return RealFingerprintingCanvasRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintingHardwareRepository(
        database: FingerprintProtectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): FingerprintingHardwareRepository {
        return RealFingerprintingHardwareRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintingScreenSizeRepository(
        database: FingerprintProtectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): FingerprintingScreenSizeRepository {
        return RealFingerprintingScreenSizeRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintingTemporaryStorageRepository(
        database: FingerprintProtectionDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): FingerprintingTemporaryStorageRepository {
        return RealFingerprintingTemporaryStorageRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideFingerprintProtectionSeedRepository(): FingerprintProtectionSeedRepository {
        return RealFingerprintProtectionSeedRepository()
    }
}
