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
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.data.store.api.DatabaseExecutor
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit.SECONDS

private const val DEFAULT_POOL_SIZE = 6
private const val CUSTOM_KEEP_ALIVE = 60L
private const val DEFAULT_KEEP_ALIVE = 0L
private const val DEFAULT_QUEUE_SIZE = DEFAULT_POOL_SIZE * 2

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RoomDatabaseProviderImplTest {

    private val context: Context = mock()
    private val databaseProviderFeature: DatabaseProviderFeature = FakeFeatureToggleFactory.create(DatabaseProviderFeature::class.java)
    private val roomDatabaseBuilderFactory: RoomDatabaseBuilderFactory = mock()
    private val mockDatabase = mock<TestDatabase>()
    private val mockRoomBuilder: RoomDatabase.Builder<TestDatabase> = mock()
    private lateinit var subject: RoomDatabaseProviderImpl

    @Before
    fun setUp() {
        whenever(roomDatabaseBuilderFactory.createBuilder(eq(context), eq(TestDatabase::class.java), eq("test.db")))
            .thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.addMigrations(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.fallbackToDestructiveMigration()).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.openHelperFactory(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.enableMultiInstanceInvalidation()).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.setJournalMode(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.addCallback(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.fallbackToDestructiveMigrationFrom(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.setQueryExecutor(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.setTransactionExecutor(any())).thenReturn(mockRoomBuilder)
        whenever(mockRoomBuilder.build()).thenReturn(mockDatabase)
    }

    @Test
    fun whenFeatureFlagDisabledAndCustomExecutorThenLegacyExecutorsAreUsed() = runTest {
        prepareSubject(flagEnabled = false)
        val config = RoomDatabaseConfig(
            executor = DatabaseExecutor.Custom(
                transactionPoolSize = 2,
                queryPoolSize = 4,
            ),
        )

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).setQueryExecutor(
            argThat {
                (this as ThreadPoolExecutor).let {
                    it.corePoolSize == 4 && it.maximumPoolSize == 4
                }
            },
        )
        verify(mockRoomBuilder).setTransactionExecutor(
            argThat {
                this is ExecutorService
            },
        )
    }

    @Test
    fun whenFeatureFlagEnabledAndCustomExecutorWithExplicitQueueSizesThenCustomExecutorsAreCreated() = runTest {
        prepareSubject(flagEnabled = true)
        databaseProviderFeature.self().setRawStoredState(State(enable = true))
        val customExecutor = DatabaseExecutor.Custom(
            transactionPoolSize = 2,
            queryPoolSize = 4,
            transactionQueueSize = 6,
            queryQueueSize = 10,
        )
        val config = RoomDatabaseConfig(executor = customExecutor)
        val queryArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()
        val transactionArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).setQueryExecutor(queryArgumentCaptor.capture())
        with(queryArgumentCaptor.firstValue) {
            this.let {
                assertEquals(customExecutor.queryPoolSize, it.corePoolSize)
                assertEquals(customExecutor.queryPoolSize, it.maximumPoolSize)
                assertTrue(it.queue is ArrayBlockingQueue)
                assertEquals(customExecutor.queryQueueSize, it.queue.size + it.queue.remainingCapacity())
                assertTrue(it.allowsCoreThreadTimeOut())
                assertEquals(CUSTOM_KEEP_ALIVE, it.getKeepAliveTime(SECONDS))
            }
        }
        verify(mockRoomBuilder).setTransactionExecutor(transactionArgumentCaptor.capture())
        with(transactionArgumentCaptor.firstValue) {
            this.let {
                assertEquals(customExecutor.transactionPoolSize, it.corePoolSize)
                assertEquals(customExecutor.transactionPoolSize, it.maximumPoolSize)
                assertTrue(it.queue is ArrayBlockingQueue)
                assertEquals(customExecutor.transactionQueueSize, it.queue.size + it.queue.remainingCapacity())
                assertTrue(it.allowsCoreThreadTimeOut())
                assertEquals(CUSTOM_KEEP_ALIVE, it.getKeepAliveTime(SECONDS))
            }
        }
    }

    @Test
    fun whenFeatureFlagEnabledAndDefaultExecutorThenDefaultExecutorIsUsed() = runTest {
        prepareSubject(flagEnabled = true)
        val config = RoomDatabaseConfig(executor = DatabaseExecutor.Default)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).setQueryExecutor(
            argThat {
                this is ExecutorService
            },
        )
        verify(mockRoomBuilder).setTransactionExecutor(
            argThat {
                this is ExecutorService
            },
        )
    }

    @Test
    fun whenFeatureFlagDisabledAndDefaultExecutorThenNoExecutorsAreSet() = runTest {
        prepareSubject(flagEnabled = false)
        val config = RoomDatabaseConfig(executor = DatabaseExecutor.Default)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder, never()).setTransactionExecutor(any())
        verify(mockRoomBuilder, never()).setQueryExecutor(any())
    }

    @Test
    fun whenMigrationsProvidedThenMigrationsArePassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val migration = mock<Migration>()
        val config = RoomDatabaseConfig(migrations = listOf(migration))

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).addMigrations(migration)
    }

    @Test
    fun whenFallbackToDestructiveMigrationEnabledThenFlagIsPassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val config = RoomDatabaseConfig(fallbackToDestructiveMigration = true)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).fallbackToDestructiveMigration()
    }

    @Test
    fun whenOpenHelperFactoryProvidedThenFactoryIsPassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val mockFactory = mock<SupportSQLiteOpenHelper.Factory>()
        val config = RoomDatabaseConfig(openHelperFactory = mockFactory)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).openHelperFactory(mockFactory)
    }

    @Test
    fun whenMultiInstanceInvalidationEnabledThenFlagIsPassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val config = RoomDatabaseConfig(enableMultiInstanceInvalidation = true)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).enableMultiInstanceInvalidation()
    }

    @Test
    fun whenJournalModeProvidedThenJournalModeIsPassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val journalMode = RoomDatabase.JournalMode.TRUNCATE
        val config = RoomDatabaseConfig(journalMode = journalMode)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).setJournalMode(journalMode)
    }

    @Test
    fun whenCallbacksProvidedThenCallbacksArePassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val callback = mock<RoomDatabase.Callback>()
        val config = RoomDatabaseConfig(callbacks = listOf(callback))

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).addCallback(callback)
    }

    @Test
    fun whenFallbackToDestructiveMigrationFromVersionProvidedThenVersionsArePassedToBuilder() = runTest {
        prepareSubject(flagEnabled = true)
        val versions = listOf(1, 2, 3)
        val config = RoomDatabaseConfig(fallbackToDestructiveMigrationFromVersion = versions)

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).fallbackToDestructiveMigrationFrom(1, 2, 3)
    }

    @Test
    fun whenEmptyConfigProvidedThenDefaultValuesArePassedToBuilder() = runTest {
        prepareSubject(flagEnabled = false)
        val config = RoomDatabaseConfig()

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder, never()).setQueryExecutor(any())
        verify(mockRoomBuilder, never()).setTransactionExecutor(any())
        verify(mockRoomBuilder, never()).addMigrations(any())
        verify(mockRoomBuilder, never()).fallbackToDestructiveMigration()
        verify(mockRoomBuilder, never()).openHelperFactory(any())
        verify(mockRoomBuilder, never()).enableMultiInstanceInvalidation()
        verify(mockRoomBuilder, never()).setJournalMode(any())
        verify(mockRoomBuilder, never()).addCallback(any())
    }

    @Test
    fun whenDefaultExecutorUsedThenThreadPoolExecutorIsCreatedWithDefaultConfig() = runTest {
        prepareSubject(flagEnabled = true)
        val config = RoomDatabaseConfig(executor = DatabaseExecutor.Default)
        val queryArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()
        val transactionArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder).setQueryExecutor(queryArgumentCaptor.capture())
        with(queryArgumentCaptor.firstValue) {
            this.let {
                assertEquals(DEFAULT_POOL_SIZE, it.corePoolSize)
                assertEquals(DEFAULT_POOL_SIZE, it.maximumPoolSize)
                assertEquals(DEFAULT_KEEP_ALIVE, it.getKeepAliveTime(SECONDS))
                assertTrue(it.queue is ArrayBlockingQueue)
                assertEquals(DEFAULT_QUEUE_SIZE, it.queue.remainingCapacity() + it.queue.size)
            }
        }
        verify(mockRoomBuilder).setTransactionExecutor(transactionArgumentCaptor.capture())
        with(transactionArgumentCaptor.firstValue) {
            this.let {
                assertEquals(DEFAULT_POOL_SIZE, it.corePoolSize)
                assertEquals(DEFAULT_POOL_SIZE, it.maximumPoolSize)
                assertEquals(DEFAULT_KEEP_ALIVE, it.getKeepAliveTime(SECONDS))
                assertTrue(it.queue is ArrayBlockingQueue)
                assertEquals(DEFAULT_QUEUE_SIZE, it.queue.remainingCapacity() + it.queue.size)
            }
        }
    }

    @Test
    fun whenSameCustomExecutorConfigUsedTwiceThenDifferentInstancesAreCreated() = runTest {
        prepareSubject(flagEnabled = true)
        val customExecutor = DatabaseExecutor.Custom(transactionPoolSize = 2, queryPoolSize = 4)
        val config = RoomDatabaseConfig(executor = customExecutor)
        val queryArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()
        val transactionArgumentCaptor = argumentCaptor<ThreadPoolExecutor>()

        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)
        subject.buildRoomDatabase(TestDatabase::class.java, "test.db", config)

        verify(mockRoomBuilder, times(2)).setQueryExecutor(queryArgumentCaptor.capture())
        verify(mockRoomBuilder, times(2)).setTransactionExecutor(transactionArgumentCaptor.capture())
        assertNotEquals(queryArgumentCaptor.firstValue, queryArgumentCaptor.secondValue)
        assertNotEquals(transactionArgumentCaptor.firstValue, transactionArgumentCaptor.secondValue)
    }

    // Test entity for testing purposes
    @androidx.room.Entity(tableName = "test_entity")
    data class TestEntity(
        @androidx.room.PrimaryKey val id: Int,
        val name: String,
    )

    // Test database class for testing purposes
    @androidx.room.Database(version = 1, entities = [TestEntity::class], exportSchema = false)
    abstract class TestDatabase : RoomDatabase() {
        // Empty implementation for testing
    }

    private fun prepareSubject(flagEnabled: Boolean) {
        databaseProviderFeature.self().setRawStoredState(State(enable = flagEnabled))
        subject = RoomDatabaseProviderImpl(
            context,
            roomDatabaseBuilderFactory,
            { RealDatabaseExecutorProvider(databaseProviderFeature) },
        )
    }
}
