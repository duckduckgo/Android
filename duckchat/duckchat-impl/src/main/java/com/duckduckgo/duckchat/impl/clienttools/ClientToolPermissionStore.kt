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

package com.duckduckgo.duckchat.impl.clienttools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier

/**
 * The user's Always and Never answers for Duck.ai client tools, keyed by tool name.
 * They persist across restarts and the Fire Button; only [clear] and [clearAll] remove them.
 */
interface ClientToolPermissionStore {
    /** The remembered choice, or null when the user has not chosen Always or Never for [toolName]. */
    suspend fun get(toolName: String): ClientToolPermission?

    suspend fun set(
        toolName: String,
        permission: ClientToolPermission,
    )

    suspend fun clear(toolName: String)

    suspend fun clearAll()

    fun observe(): Flow<Map<String, ClientToolPermission>>
}

enum class ClientToolPermission {
    ALWAYS_ALLOW,
    NEVER_ALLOW,
}

@ContributesBinding(AppScope::class)
class RealClientToolPermissionStore @Inject constructor(
    @ClientToolPermissions private val store: DataStore<Preferences>,
) : ClientToolPermissionStore {

    override suspend fun get(toolName: String): ClientToolPermission? = observe().firstOrNull()?.get(toolName)

    override suspend fun set(
        toolName: String,
        permission: ClientToolPermission,
    ) {
        store.edit { it[stringPreferencesKey(toolName)] = permission.name }
    }

    override suspend fun clear(toolName: String) {
        store.edit { it.remove(stringPreferencesKey(toolName)) }
    }

    override suspend fun clearAll() {
        store.edit { it.clear() }
    }

    // A value this build does not know reads as no choice, so at worst the user is asked again.
    override fun observe(): Flow<Map<String, ClientToolPermission>> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            preferences.asMap().mapNotNull { (key, value) ->
                ClientToolPermission.entries.firstOrNull { it.name == value }?.let { key.name to it }
            }.toMap()
        }
        .distinctUntilChanged()
}

private val Context.clientToolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "duckai_client_tool_permissions",
)

@Qualifier
internal annotation class ClientToolPermissions

@BindingContainer
@ContributesTo(AppScope::class)
object ClientToolPermissionStoreModule {
    @Provides
    @ClientToolPermissions
    fun provideClientToolPermissionsDataStore(context: Context): DataStore<Preferences> = context.clientToolPermissionsDataStore
}
