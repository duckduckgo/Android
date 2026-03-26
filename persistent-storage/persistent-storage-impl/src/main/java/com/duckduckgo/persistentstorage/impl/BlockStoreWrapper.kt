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

package com.duckduckgo.persistentstorage.impl

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

interface BlockStoreWrapper {
    val isPlayServicesAvailable: Boolean
    suspend fun isEndToEndEncryptionAvailable(): Boolean
    suspend fun retrieveBytes(key: String): ByteArray?
    suspend fun storeBytes(key: String, bytes: ByteArray, shouldBackupToCloud: Boolean)
    suspend fun deleteBytes(key: String)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBlockStoreWrapper @Inject constructor(
    private val context: Context,
) : BlockStoreWrapper {

    private val client by lazy { Blockstore.getClient(context) }

    override val isPlayServicesAvailable: Boolean by lazy {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    override suspend fun isEndToEndEncryptionAvailable(): Boolean {
        return client.isEndToEndEncryptionAvailable.await()
    }

    override suspend fun retrieveBytes(key: String): ByteArray? {
        val request = RetrieveBytesRequest.Builder()
            .setKeys(listOf(key))
            .build()
        val response = client.retrieveBytes(request).await()
        return response.blockstoreDataMap[key]?.bytes
    }

    override suspend fun storeBytes(key: String, bytes: ByteArray, shouldBackupToCloud: Boolean) {
        val storeBytesData = StoreBytesData.Builder()
            .setKey(key)
            .setBytes(bytes)
            .setShouldBackupToCloud(shouldBackupToCloud)
            .build()
        client.storeBytes(storeBytesData).await()
    }

    override suspend fun deleteBytes(key: String) {
        val request = DeleteBytesRequest.Builder()
            .setKeys(listOf(key))
            .build()
        client.deleteBytes(request).await()
    }
}
