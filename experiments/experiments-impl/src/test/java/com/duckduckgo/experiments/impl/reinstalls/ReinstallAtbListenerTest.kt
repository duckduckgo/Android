/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.experiments.impl.reinstalls

import android.os.Build
import androidx.core.content.edit
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReinstallAtbListenerTest {

    private lateinit var testee: ReinstallAtbListener

    private val mockBackupDataStore: BackupServiceDataStore = mock()
    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockDownloadsDirectoryManager: DownloadsDirectoryManager = mock()
    private val preferences = InMemorySharedPreferences()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        testee = ReinstallAtbListener(
            mockBackupDataStore,
            mockStatisticsDataStore,
            mockAppBuildConfig,
            mockDownloadsDirectoryManager,
            { preferences },
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenBeforeAtbInitIsCalledThenClearBackupServiceSharedPreferences() = runTest {
        testee.beforeAtbInit()

        verify(mockBackupDataStore).clearBackupPreferences()
    }

    @Test
    fun whenAndroidVersionIs10OrLowerThenDontCheckForDownloadsDirectory() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.Q)

        testee.beforeAtbInit()

        verify(mockDownloadsDirectoryManager, never()).getDownloadsDirectory()
    }

    @Test
    fun whenReturningUserHasBeenAlreadyCheckedThenDontCheckForDownloadsDirectory() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.R)
        setReturningUserChecked()

        testee.beforeAtbInit()

        verify(mockDownloadsDirectoryManager, never()).getDownloadsDirectory()
    }

    @Test
    fun whenDDGDirectoryIsFoundThenUpdateVariantForReturningUser() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.R)
        val mockDownloadsDirectory: File = mock {
            on { list() } doReturn arrayOf("DuckDuckGo")
        }
        whenever(mockDownloadsDirectoryManager.getDownloadsDirectory()).thenReturn(mockDownloadsDirectory)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
        assertTrue(isReturningUserChecked())
    }

    @Test
    fun whenDDGDirectoryIsNotFoundThenVariantForReturningUserIsNotSet() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.R)
        val mockDownloadsDirectory: File = mock {
            on { list() } doReturn emptyArray()
        }
        whenever(mockDownloadsDirectoryManager.getDownloadsDirectory()).thenReturn(mockDownloadsDirectory)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore, never()).variant = REINSTALL_VARIANT
        assertTrue(isReturningUserChecked())
    }

    @Test
    fun whenDDGDirectoryIsNotFoundThenCreateIt() = runTest {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(Build.VERSION_CODES.R)
        val mockDownloadsDirectory: File = mock {
            on { list() } doReturn emptyArray()
        }
        whenever(mockDownloadsDirectoryManager.getDownloadsDirectory()).thenReturn(mockDownloadsDirectory)

        testee.beforeAtbInit()

        verify(mockDownloadsDirectoryManager).createNewDirectory("DuckDuckGo")
        assertTrue(isReturningUserChecked())
    }

    private fun isReturningUserChecked(): Boolean {
        return preferences.getBoolean("RETURNING_USER_CHECKED_TAG", false)
    }

    private fun setReturningUserChecked() {
        preferences.edit(commit = true) { putBoolean("RETURNING_USER_CHECKED_TAG", true) }
    }
}
