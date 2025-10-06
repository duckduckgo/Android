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

package com.duckduckgo.pir.impl.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM pir_user_profile")
    fun getUserProfiles(): List<UserProfile>

    @Query("SELECT * FROM pir_user_profile WHERE id IN (:ids)")
    fun getUserProfilesWithIds(ids: List<Long>): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUserProfile(userProfile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUserProfiles(userProfiles: List<UserProfile>): List<Long>

    @Query("DELETE from pir_user_profile WHERE id IN (:ids)")
    fun deleteUserProfiles(ids: List<Long>)

    @Query("DELETE from pir_user_profile")
    fun deleteAllProfiles()

    @Transaction
    fun updateUserProfiles(
        profilesToAdd: List<UserProfile>,
        profilesToUpdate: List<UserProfile>,
        profileIdsToDelete: List<Long>,
    ) {
        if (profileIdsToDelete.isNotEmpty()) {
            deleteUserProfiles(profileIdsToDelete)
        }

        if (profilesToAdd.isNotEmpty() || profilesToUpdate.isNotEmpty()) {
            insertUserProfiles(profilesToAdd + profilesToUpdate)
        }
    }
}
