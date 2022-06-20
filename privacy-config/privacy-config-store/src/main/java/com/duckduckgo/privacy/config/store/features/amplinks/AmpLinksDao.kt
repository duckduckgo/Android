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

package com.duckduckgo.privacy.config.store.features.amplinks

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.privacy.config.store.AmpKeywordEntity
import com.duckduckgo.privacy.config.store.AmpLinkFormatEntity
import com.duckduckgo.privacy.config.store.AmpLinkExceptionEntity

@Dao
abstract class AmpLinksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllExceptions(domains: List<AmpLinkExceptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllAmpLinkFormats(ampLinkFormats: List<AmpLinkFormatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllAmpKeywords(ampKeywords: List<AmpKeywordEntity>)

    @Transaction
    open fun updateAll(domains: List<AmpLinkExceptionEntity>, ampLinkFormats: List<AmpLinkFormatEntity>, ampKeywords: List<AmpKeywordEntity>) {
        deleteAllExceptions()
        insertAllExceptions(domains)

        deleteAllAmpLinkFormats()
        insertAllAmpLinkFormats(ampLinkFormats)

        deleteAllAmpKeywords()
        insertAllAmpKeywords(ampKeywords)
    }

    @Query("select * from amp_exceptions")
    abstract fun getAllExceptions(): List<AmpLinkExceptionEntity>

    @Query("select * from amp_link_formats")
    abstract fun getAllAmpLinkFormats(): List<AmpLinkFormatEntity>

    @Query("select * from amp_keywords")
    abstract fun getAllAmpKeywords(): List<AmpKeywordEntity>

    @Query("delete from amp_exceptions")
    abstract fun deleteAllExceptions()

    @Query("delete from amp_link_formats")
    abstract fun deleteAllAmpLinkFormats()

    @Query("delete from amp_keywords")
    abstract fun deleteAllAmpKeywords()
}
