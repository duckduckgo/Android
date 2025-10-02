/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
abstract class PopupDismissDomainsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: PopupDismissDomain)

    @Query("SELECT * FROM popup_dismiss_domains WHERE domain = :domain")
    abstract fun query(domain: String): Flow<PopupDismissDomain?>

    @Query("DELETE FROM popup_dismiss_domains WHERE dismissedAt < :time")
    abstract suspend fun removeEntriesOlderThan(time: Instant)

    @Query("DELETE FROM popup_dismiss_domains")
    abstract suspend fun removeAllEntries()
}
