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

package com.duckduckgo.duckchat.impl.promo

import android.annotation.SuppressLint
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class PromoRecord(
    val dismissedCampaignId: String? = null,
    val campaignId: String? = null,
    val promoId: String? = null,
    val terms: PromoTerms? = null,
    val lastRedeemAttemptMillis: Long? = null,
)

data class PromoTerms(
    val multiplier: Float,
    val expiresAtMillis: Long,
)

class PromoRecordStorageException(cause: Throwable) : Exception(cause)

class PromoRecordCorruptedException(cause: Throwable) : Exception(cause)

interface PromoCampaignDataStore {
    /**
     * Emits the stored promo record, or null if none is stored, each time it changes.
     * Fails with [PromoRecordStorageException] if the record can't be read,
     * or with [PromoRecordCorruptedException] if it can't be decoded.
     */
    val promoRecord: Flow<PromoRecord?>

    /**
     * @return an instance of the promo record, or null if none is stored.
     *
     * @throws PromoRecordStorageException if the record can't be read.
     * @throws PromoRecordCorruptedException if the record can't be decoded.
     */
    suspend fun getPromoRecord(): PromoRecord?

    /**
     * Replaces the stored record with the result of [transform] in a single transaction.
     * [transform] must not have side effects. Returning `null` causes the record to be removed.
     *
     * @return the stored record, or null if it was removed.
     *
     * @throws PromoRecordStorageException if the record can't be read or written.
     * @throws PromoRecordCorruptedException if the stored record can't be decoded. It's left as is.
     */
    suspend fun updatePromoRecord(transform: (PromoRecord?) -> PromoRecord?): PromoRecord?

    /**
     * Removes the stored record.
     *
     * @throws PromoRecordStorageException if the record can't be written.
     */
    suspend fun clear()
}

@SuppressLint("AvoidComputationUsage")
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealPromoCampaignDataStore @Inject constructor(
    @param:DuckChat private val store: DataStore<Preferences>,
    private val dispatcherProvider: DispatcherProvider,
    moshi: Moshi,
) : PromoCampaignDataStore {
    private val jsonAdapter: JsonAdapter<PromoRecord> by lazy {
        moshi.newBuilder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(PromoRecord::class.java)
    }

    override val promoRecord: Flow<PromoRecord?> = store.data
        .catch { error -> throw if (error is IOException) PromoRecordStorageException(error) else error }
        .map { preferences -> preferences[DUCK_AI_PROMO_RECORD] }
        .distinctUntilChanged()
        .map(::decode)
        .flowOn(dispatcherProvider.computation())

    override suspend fun getPromoRecord(): PromoRecord? = promoRecord.firstOrNull()

    override suspend fun updatePromoRecord(transform: (PromoRecord?) -> PromoRecord?): PromoRecord? {
        var storedRecord: PromoRecord? = null
        try {
            store.edit { preferences ->
                withContext(dispatcherProvider.computation()) {
                    val currentRecord = decode(preferences[DUCK_AI_PROMO_RECORD])
                    val updatedRecord = transform(currentRecord)
                    when (updatedRecord) {
                        currentRecord -> Unit // Skip write since record has not changed
                        null -> preferences.remove(DUCK_AI_PROMO_RECORD)
                        else -> preferences[DUCK_AI_PROMO_RECORD] = jsonAdapter.toJson(updatedRecord)
                    }
                    storedRecord = updatedRecord
                }
            }
        } catch (e: IOException) {
            throw PromoRecordStorageException(e)
        }
        return storedRecord
    }

    override suspend fun clear() {
        try {
            store.edit { preferences -> preferences.remove(DUCK_AI_PROMO_RECORD) }
        } catch (e: IOException) {
            throw PromoRecordStorageException(e)
        }
    }

    private fun decode(json: String?): PromoRecord? {
        if (json == null) {
            return null
        }
        val record = try {
            jsonAdapter.fromJson(json)
        } catch (e: IOException) {
            throw PromoRecordCorruptedException(e)
        } catch (e: JsonDataException) {
            throw PromoRecordCorruptedException(e)
        }
        return record ?: throw PromoRecordCorruptedException(
            JsonDataException("Stored promo record's JSON is the literal 'null'"),
        )
    }

    private companion object {
        val DUCK_AI_PROMO_RECORD = stringPreferencesKey("DUCK_AI_PROMO_RECORD")
    }
}
