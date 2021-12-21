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

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.lang.Thread.UncaughtExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class VpnUncaughtExceptionHandler(
    private val context: Context,
    private val originalHandler: UncaughtExceptionHandler?,
    private val coroutineScope: CoroutineScope,
) : UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (throwable is OutOfMemoryError) {
            Timber.e("Out of memory; triggering a VPN restart")

            restartVpn()
        } else {
            Timber.e(throwable, "VPN uncaughtException")
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun restartVpn() {
        coroutineScope.launch {
            TrackerBlockingVpnService.restartVpnService(context, forceGc = true)
        }
    }
}

@Module
@ContributesTo(scope = AppScope::class)
class VpnExceptionModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providesVpnUncaughtExceptionHandler(
        context: Context,
        @AppCoroutineScope vpnCoroutineScope: CoroutineScope
    ): VpnUncaughtExceptionHandler {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        return VpnUncaughtExceptionHandler(context, originalHandler, vpnCoroutineScope)
    }
}
