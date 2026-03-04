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

package com.duckduckgo.persistent.storage.impl

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

/**
 * Provider for [BlockstoreClient] and Play Services availability.
 * Extracted to enable unit testing of [RealPersistentStorage].
 */
interface BlockstoreClientProvider {
    val client: BlockstoreClient
    val isPlayServicesAvailable: Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealBlockstoreClientProvider @Inject constructor(
    private val context: Context,
) : BlockstoreClientProvider {

    override val client: BlockstoreClient by lazy {
        Blockstore.getClient(context)
    }

    override val isPlayServicesAvailable: Boolean by lazy {
        val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val available = result == ConnectionResult.SUCCESS
        logcat { "PersistentStorage: Play Services available = $available (result code: $result)" }
        available
    }
}
