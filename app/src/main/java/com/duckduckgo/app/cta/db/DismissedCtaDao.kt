/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.cta.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DismissedCtaDao {

    @Query("select * from dismissed_cta")
    abstract fun dismissedCtas(): Flow<List<DismissedCta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(dismissedCta: DismissedCta)

    @Query("select count(1) > 0 from dismissed_cta where ctaId = :ctaId")
    abstract fun exists(ctaId: CtaId): Boolean
}
