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

package com.duckduckgo.app.install

import android.content.SharedPreferences
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealReinstallDetectorTest {

    private val dispatcherProvider = DefaultDispatcherProvider()
    private val ddgDownloadsDirectory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "DuckDuckGo",
    )

    @Before
    fun setUp() {
        ddgDownloadsDirectory.deleteRecursively()
    }

    @After
    fun tearDown() {
        ddgDownloadsDirectory.deleteRecursively()
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryIsMissingThenIsNotReinstallAndDirectoryIsCreated() = runTest {
        val preferences = InMemorySharedPreferences()

        assertFalse(reinstallDetectorWith(preferences).isAppReinstall())
        assertTrue(ddgDownloadsDirectory.exists())
        assertFalse(preferences.getBoolean(APP_REINSTALLED_KEY, true))
    }

    @Test
    @Config(sdk = [34])
    fun whenDdgDownloadsDirectoryExistsThenIsReinstall() = runTest {
        ddgDownloadsDirectory.mkdirs()
        val preferences = InMemorySharedPreferences()

        assertTrue(reinstallDetectorWith(preferences).isAppReinstall())
        assertTrue(preferences.getBoolean(APP_REINSTALLED_KEY, false))
    }

    @Test
    @Config(sdk = [34])
    fun whenAnswerIsAlreadySavedThenItIsReusedWithoutCheckingTheDirectory() = runTest {
        val preferences = InMemorySharedPreferences().apply { putBoolean(APP_REINSTALLED_KEY, true) }

        assertTrue(reinstallDetectorWith(preferences).isAppReinstall())
        assertFalse(ddgDownloadsDirectory.exists())
    }

    @Test
    @Config(sdk = [29])
    fun whenAndroidVersionIsBelow11ThenIsNotReinstallAndDirectoryIsNotCreated() = runTest {
        ddgDownloadsDirectory.parentFile?.mkdirs()

        assertFalse(reinstallDetectorWith(InMemorySharedPreferences()).isAppReinstall())
        assertFalse(ddgDownloadsDirectory.exists())
    }

    private fun reinstallDetectorWith(preferences: SharedPreferences): RealReinstallDetector {
        val sharedPreferencesProvider: SharedPreferencesProvider = mock()
        whenever(sharedPreferencesProvider.getSharedPreferences(any(), any(), any())).thenReturn(preferences)
        return RealReinstallDetector(
            dispatcherProvider = dispatcherProvider,
            sharedPreferencesProvider = sharedPreferencesProvider,
        )
    }

    private companion object {
        const val APP_REINSTALLED_KEY = "appReinstalled"
    }
}
