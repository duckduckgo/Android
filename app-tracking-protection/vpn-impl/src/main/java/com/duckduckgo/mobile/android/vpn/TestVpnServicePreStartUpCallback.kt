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

package com.duckduckgo.mobile.android.vpn

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.VpnServicePreStartupCallback
import com.duckduckgo.vpn.di.VpnCoroutineScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class TestVpnServicePreStartUpCallback @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @VpnCoroutineScope private val coroutineScope: CoroutineScope,
) : VpnServicePreStartupCallback {
    override fun supportsFeature(vpnFeature: VpnFeature): Boolean {
        return true
    }

    override suspend fun prepBeforeServiceStarts() {
        Timber.d("KL starting prepBeforeServiceStarts")
        delay(5_000)
        Timber.d("KL complete prepBeforeServiceStarts")
    }
}
