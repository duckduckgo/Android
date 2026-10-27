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

package com.duckduckgo.app.global.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CorruptionReportingOpenHelperFactoryTest {

    private val context: Context = mock()
    private val roomCallback: SupportSQLiteOpenHelper.Callback = mock()
    private val delegateFactory: SupportSQLiteOpenHelper.Factory = mock()
    private val openHelper: SupportSQLiteOpenHelper = mock()
    private val db: SupportSQLiteDatabase = mock()
    private val reported = mutableListOf<SupportSQLiteDatabase>()

    private val configuration = SupportSQLiteOpenHelper.Configuration(
        context = context,
        name = "app.db",
        callback = roomCallback,
        useNoBackupDirectory = true,
        allowDataLossOnRecovery = true,
    )

    @Before
    fun setup() {
        whenever(delegateFactory.create(any())).thenReturn(openHelper)
    }

    @Test
    fun whenCreateThenReturnsDelegateOpenHelperWithConfigurationPreserved() {
        val helper = createFactory().create(configuration)

        val wrapped = delegateConfiguration()
        assertSame(openHelper, helper)
        assertSame(context, wrapped.context)
        assertEquals("app.db", wrapped.name)
        assertEquals(true, wrapped.useNoBackupDirectory)
        assertEquals(true, wrapped.allowDataLossOnRecovery)
        assertEquals(roomCallback.version, wrapped.callback.version)
    }

    @Test
    fun whenCorruptionReportedThenReportsDatabaseBeforeDelegateDeletesIt() {
        doAnswer { reported.add(mock()) }.whenever(roomCallback).onCorruption(db)
        createFactory().create(configuration)

        delegateConfiguration().callback.onCorruption(db)

        assertSame(db, reported.first())
        verify(roomCallback).onCorruption(db)
    }

    @Test
    fun whenReportingFailsThenDelegateStillHandlesCorruption() {
        val factory = CorruptionReportingOpenHelperFactory(onCorruption = { throw IllegalStateException() }, delegate = delegateFactory)

        factory.create(configuration)
        delegateConfiguration().callback.onCorruption(db)

        verify(roomCallback).onCorruption(db)
    }

    @Test
    fun whenLifecycleCallbacksInvokedThenForwardedToDelegate() {
        createFactory().create(configuration)
        val callback = delegateConfiguration().callback

        callback.onConfigure(db)
        callback.onCreate(db)
        callback.onUpgrade(db, 1, 2)
        callback.onDowngrade(db, 2, 1)
        callback.onOpen(db)

        inOrder(roomCallback) {
            verify(roomCallback).onConfigure(db)
            verify(roomCallback).onCreate(db)
            verify(roomCallback).onUpgrade(db, 1, 2)
            verify(roomCallback).onDowngrade(db, 2, 1)
            verify(roomCallback).onOpen(db)
        }
    }

    private fun createFactory() = CorruptionReportingOpenHelperFactory(onCorruption = { reported.add(it) }, delegate = delegateFactory)

    private fun delegateConfiguration(): SupportSQLiteOpenHelper.Configuration {
        val captor = argumentCaptor<SupportSQLiteOpenHelper.Configuration>()
        verify(delegateFactory).create(captor.capture())
        return captor.firstValue
    }
}
