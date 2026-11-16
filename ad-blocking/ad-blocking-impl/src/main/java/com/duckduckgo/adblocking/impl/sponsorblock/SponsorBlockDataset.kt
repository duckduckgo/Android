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

package com.duckduckgo.adblocking.impl.sponsorblock

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.duckduckgo.adblocking.impl.BuildConfig
import com.duckduckgo.common.utils.versionedset.VersionedSetDelta
import com.duckduckgo.common.utils.versionedset.VersionedSetUpdate
import com.duckduckgo.common.utils.versionedset.updateFrom
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import retrofit2.http.GET
import retrofit2.http.Query as HttpQuery

interface SponsorBlockDatasetService {
    @GET(BuildConfig.SPONSOR_BLOCK_BASE_URL + "/revision")
    suspend fun revision(): SponsorBlockRevisionResponse

    @GET(BuildConfig.SPONSOR_BLOCK_BASE_URL + "/hashPrefix")
    suspend fun hashPrefixes(@HttpQuery("revision") revision: Int): SponsorBlockDeltaResponse
}

data class SponsorBlockRevisionResponse(val revision: Int?)

data class SponsorBlockDeltaResponse(
    val insert: Set<String>?,
    val delete: Set<String>?,
    val replace: Boolean?,
    val revision: Int?,
)

@Entity(tableName = "sponsor_prefixes")
data class SponsorBlockPrefix(@PrimaryKey val prefix: String)

@Entity(tableName = "sponsor_revision")
data class SponsorBlockRevision(@PrimaryKey val id: Int = 0, val revision: Int)

@Dao
abstract class SponsorBlockDatasetDao {
    @Query("SELECT revision FROM sponsor_revision WHERE id = 0")
    abstract suspend fun revision(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM sponsor_prefixes WHERE prefix = :prefix)")
    abstract suspend fun contains(prefix: String): Boolean

    @Query("DELETE FROM sponsor_prefixes")
    abstract suspend fun clear()

    @Query("DELETE FROM sponsor_prefixes WHERE prefix IN (:prefixes)")
    abstract suspend fun delete(prefixes: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(prefixes: List<SponsorBlockPrefix>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveRevision(revision: SponsorBlockRevision)

    @Transaction
    open suspend fun apply(delta: VersionedSetDelta<String>) {
        val current = revision() ?: 0
        when (val update = delta.updateFrom(current)) {
            VersionedSetUpdate.Ignore -> return
            is VersionedSetUpdate.Replace -> clear()
            is VersionedSetUpdate.Delta -> update.delete.toList().chunked(900).forEach { delete(it) }
        }
        insert(delta.insert.map(::SponsorBlockPrefix))
        saveRevision(SponsorBlockRevision(revision = delta.revision))
    }

    @Transaction
    open suspend fun hasSegments(prefix: String): Boolean = revision() == null || contains(prefix)
}

@Database(entities = [SponsorBlockPrefix::class, SponsorBlockRevision::class], version = 1, exportSchema = false)
abstract class SponsorBlockDatasetDatabase : RoomDatabase() {
    abstract fun dao(): SponsorBlockDatasetDao
}

@BindingContainer
@ContributesTo(AppScope::class)
object SponsorBlockDatasetModule {
    @Provides
    @SingleIn(AppScope::class)
    fun database(provider: DatabaseProvider): SponsorBlockDatasetDatabase =
        provider.buildRoomDatabase(SponsorBlockDatasetDatabase::class.java, "sponsor_block_dataset.db")

    @Provides
    fun dao(database: SponsorBlockDatasetDatabase): SponsorBlockDatasetDao = database.dao()

    @Provides
    @SingleIn(AppScope::class)
    @android.annotation.SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun service(@javax.inject.Named("api") retrofit: retrofit2.Retrofit): SponsorBlockDatasetService =
        retrofit.create(SponsorBlockDatasetService::class.java)
}
