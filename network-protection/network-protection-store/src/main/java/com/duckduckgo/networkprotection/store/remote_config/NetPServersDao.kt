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

package com.duckduckgo.networkprotection.store.remote_config

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NetPServersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(server: List<NetPEgressServer>)

    @Query("delete from netp_egress_servers")
    suspend fun clearAll()

    @Query("SELECT * from netp_egress_servers")
    suspend fun getServers(): List<NetPEgressServer>

    @Query("DELETE from netp_selected_egress_server_name")
    suspend fun clearSelectedServer()

    @Query("SELECT * from netp_selected_egress_server_name LIMIT 1")
    suspend fun getSelectedServer(): SelectedEgressServer?

    @Query("INSERT or REPLACE into netp_selected_egress_server_name (id, name) values (1, :name)")
    suspend fun selectServer(name: String)
}
