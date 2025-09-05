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

package com.duckduckgo.data.store.impl

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Factory for creating Room database builders to enable better testing
 */
interface RoomDatabaseBuilderFactory {
    fun <T : RoomDatabase> createBuilder(
        context: Context,
        klass: Class<T>,
        name: String,
    ): RoomDatabase.Builder<T>
}

/**
 * Real implementation of RoomDatabaseBuilderFactory that uses Room.databaseBuilder
 */
@ContributesBinding(AppScope::class)
class RealRoomDatabaseBuilderFactory @Inject constructor() : RoomDatabaseBuilderFactory {
    @SuppressLint("DenyListedApi")
    override fun <T : RoomDatabase> createBuilder(
        context: Context,
        klass: Class<T>,
        name: String,
    ): RoomDatabase.Builder<T> {
        return Room.databaseBuilder(context, klass, name)
    }
}
