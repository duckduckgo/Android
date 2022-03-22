/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Thread.UncaughtExceptionHandler
import dagger.SingleInstanceIn
import kotlinx.coroutines.NonCancellable

class VpnUncaughtExceptionHandler(
    private val originalHandler: UncaughtExceptionHandler?,
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
) : UncaughtExceptionHandler {

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable
    ) {
        Timber.e(throwable, "VPN uncaughtException")
        recordExceptionAndAllowCrash(thread, throwable)
    }

    private fun recordExceptionAndAllowCrash(
        thread: Thread,
        originalException: Throwable
    ) {
        coroutineScope.launch(dispatcherProvider.io() + NonCancellable) {
            try {
                uncaughtExceptionRepository.recordUncaughtException(originalException, UncaughtExceptionSource.GLOBAL)
                offlinePixelCountDataStore.applicationCrashCount += 1
            } catch (e: Throwable) {
                Timber.e(e, "Failed to record exception")
            } finally {
                originalHandler?.uncaughtException(thread, originalException)
            }
        }
    }
}

@Module
@ContributesTo(scope = AppScope::class)
class VpnExceptionModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providesVpnUncaughtExceptionHandler(
        @AppCoroutineScope vpnCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        offlinePixelCountDataStore: OfflinePixelCountDataStore,
        uncaughtExceptionRepository: UncaughtExceptionRepository,
    ): VpnUncaughtExceptionHandler {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        return VpnUncaughtExceptionHandler(
            originalHandler,
            vpnCoroutineScope,
            dispatcherProvider,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository
        )
    }
}
