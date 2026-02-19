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

package com.duckduckgo.autofill.impl.securestorage

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.store.db.SecureStorageDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RuntimeEnvironment

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealSecureStorageDatabaseFactoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val keyProvider: SecureStorageKeyProvider = mock()
    private val sqlCipherLoader: SqlCipherLibraryLoader = mock()
    private val timeoutException: TimeoutCancellationException = mock()

    // Use a test DatabaseProvider that creates in-memory databases
    private val testDatabaseProvider = object : DatabaseProvider {
        override fun <T : RoomDatabase> buildRoomDatabase(klass: Class<T>, name: String, config: RoomDatabaseConfig): T {
            // Create an unencrypted in-memory database for testing
            return Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), klass)
                .allowMainThreadQueries()
                .build()
        }
    }

    private lateinit var factory: RealSecureStorageDatabaseFactory
    private var createdDatabase: SecureStorageDatabase? = null

    @Before
    fun setup() = runTest {
        factory = RealSecureStorageDatabaseFactory(
            keyProvider = keyProvider,
            databaseProvider = testDatabaseProvider,
            sqlCipherLoader = sqlCipherLoader,
        )
        whenever(keyProvider.canAccessKeyStore()).thenReturn(true)
        whenever(keyProvider.getl1Key()).thenReturn(ByteArray(32))
    }

    @After
    fun tearDown() {
        createdDatabase?.close()
    }

    @Test
    fun whenLibraryLoadsSuccessfullyAndKeystoreAccessibleThenDatabaseCreated() = runTest {
        configureLibraryLoadSuccess()
        createdDatabase = factory.getDatabase()
        assertNotNull(createdDatabase)
    }

    @Test
    fun whenDatabaseRetrievedThenLibraryLoadAttempted() = runTest {
        configureLibraryLoadSuccess()
        createdDatabase = factory.getDatabase()
        verify(sqlCipherLoader).waitForLibraryLoad()
    }

    @Test
    fun whenLibraryLoadTimeoutThenReturnsNull() = runTest {
        configureLibraryLoadTimeout()
        val result = factory.getDatabase()
        assertNull(result)
    }

    @Test
    fun whenLibraryLoadFailureThenReturnsNull() = runTest {
        configureLibraryLoadFailure(RuntimeException("Library load failed"))
        val result = factory.getDatabase()
        assertNull(result)
    }

    @Test
    fun whenKeystoreNotAccessibleThenReturnsNull() = runTest {
        configureLibraryLoadSuccess()
        whenever(keyProvider.canAccessKeyStore()).thenReturn(false)
        val result = factory.getDatabase()
        assertNull(result)
    }

    @Test
    fun whenDatabaseAlreadyInitializedThenReturnsCachedInstance() = runTest {
        configureLibraryLoadSuccess()

        createdDatabase = factory.getDatabase()
        val secondResult = factory.getDatabase()

        assertSame(createdDatabase, secondResult)
        assertNotNull(secondResult)
        verify(sqlCipherLoader, times(1)).waitForLibraryLoad()
        verify(keyProvider, times(1)).canAccessKeyStore()
    }

    @Test
    fun whenMultipleThreadsCallGetDatabaseThenOnlyInitializesOnce() = runTest {
        configureLibraryLoadSuccess()

        val deferred1 = async(start = CoroutineStart.UNDISPATCHED) { factory.getDatabase() }
        val deferred2 = async(start = CoroutineStart.UNDISPATCHED) { factory.getDatabase() }
        val deferred3 = async(start = CoroutineStart.UNDISPATCHED) { factory.getDatabase() }

        createdDatabase = deferred1.await()
        val result2 = deferred2.await()
        val result3 = deferred3.await()

        assertSame(createdDatabase, result2)
        assertSame(result2, result3)
        assertNotNull(createdDatabase)
        verify(sqlCipherLoader, times(1)).waitForLibraryLoad()
        verify(keyProvider, times(1)).canAccessKeyStore()
    }

    private suspend fun configureLibraryLoadSuccess() {
        whenever(sqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.success(Unit))
    }

    private suspend fun configureLibraryLoadFailure(exception: Throwable) {
        whenever(sqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.failure(exception))
    }

    private suspend fun configureLibraryLoadTimeout() {
        whenever(sqlCipherLoader.waitForLibraryLoad()).thenReturn(Result.failure(timeoutException))
    }
}
