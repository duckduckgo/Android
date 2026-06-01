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

package com.duckduckgo.app.location.migration

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.location.permissions.api.LocationPermissionEntity
import com.duckduckgo.location.permissions.api.LocationPermissionType
import com.duckduckgo.location.permissions.api.LocationPermissionsRepository
import dagger.Lazy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class LocationPermissionsDbCopyObserverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository: LocationPermissionsRepository = mock()
    private val owner: LifecycleOwner = mock()

    private lateinit var db: AppDatabase
    private lateinit var testee: LocationPermissionsDbCopyObserver

    @Before
    fun before() {
        context.getSharedPreferences(LocationPermissionsDbCopyObserver.FILENAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The legacy table is no longer a Room entity, so recreate it manually for the test.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS `locationPermissions` (`domain` TEXT NOT NULL, `permission` INTEGER NOT NULL, PRIMARY KEY(`domain`))",
        )

        testee = LocationPermissionsDbCopyObserver(context, { db }, Lazy { repository })
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenLegacyRowsExistThenTheyAreCopiedIntoTheRepository() {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `locationPermissions` (`domain`, `permission`) VALUES ('example.com', ${LocationPermissionType.ALLOW_ALWAYS.value})",
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `locationPermissions` (`domain`, `permission`) VALUES ('denied.com', ${LocationPermissionType.DENY_ALWAYS.value})",
        )

        testee.onCreate(owner)

        verify(repository).savePermissionEntity(LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS))
        verify(repository).savePermissionEntity(LocationPermissionEntity("denied.com", LocationPermissionType.DENY_ALWAYS))
    }

    @Test
    fun whenCopyHasAlreadyRunThenItDoesNotRunAgain() {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO `locationPermissions` (`domain`, `permission`) VALUES ('example.com', ${LocationPermissionType.ALLOW_ALWAYS.value})",
        )

        testee.onCreate(owner)
        verify(repository).savePermissionEntity(LocationPermissionEntity("example.com", LocationPermissionType.ALLOW_ALWAYS))

        // Second launch: nothing more should be copied.
        testee.onCreate(owner)
        verify(repository, never()).savePermissionEntity(LocationPermissionEntity("denied.com", LocationPermissionType.DENY_ALWAYS))
    }

    @Test
    fun whenNoLegacyRowsThenNothingIsCopied() {
        testee.onCreate(owner)

        verifyNoInteractions(repository)
    }

    @Test
    fun whenCopiedThenFlagPersisted() {
        testee.onCreate(owner)

        val flag = context.getSharedPreferences(LocationPermissionsDbCopyObserver.FILENAME, Context.MODE_PRIVATE)
            .getBoolean(LocationPermissionsDbCopyObserver.KEY_COPIED, false)
        assertEquals(true, flag)
    }
}
