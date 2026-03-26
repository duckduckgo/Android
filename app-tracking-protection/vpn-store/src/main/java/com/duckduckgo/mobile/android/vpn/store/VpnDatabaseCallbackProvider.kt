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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import androidx.room.RoomDatabase
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import javax.inject.Provider

class VpnDatabaseCallbackProvider constructor(
    private val context: Context,
    private val vpnDatabaseProvider: Provider<VpnDatabase>,
    private val dispatcherProvider: DispatcherProvider,
    private val coroutineScope: CoroutineScope,
    private val mutex: Mutex,
) {
    fun provideCallbacks(): RoomDatabase.Callback {
        return VpnDatabaseCallback(context, vpnDatabaseProvider, dispatcherProvider, coroutineScope, mutex)
    }
}
