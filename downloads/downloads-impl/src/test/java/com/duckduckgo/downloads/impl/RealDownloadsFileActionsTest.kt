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

package com.duckduckgo.downloads.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class RealDownloadsFileActionsTest {

    private val mockContext: Context = mock()
    private val mockPackageManager: PackageManager = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockNewDownloadState: InternalNewDownloadState = mock()
    private lateinit var mockedFileProvider: MockedStatic<FileProvider>

    private val testee = RealDownloadsFileActions(
        appBuildConfig = mockAppBuildConfig,
        newDownloadState = mockNewDownloadState,
    )

    @Before
    fun setup() {
        mockedFileProvider = mockStatic(FileProvider::class.java)
        mockedFileProvider
            .`when`<Uri> { FileProvider.getUriForFile(any(), any(), any()) }
            .thenReturn(Uri.parse("content://$OWN_PACKAGE.provider/$FILE_NAME"))
        whenever(mockAppBuildConfig.applicationId).thenReturn(OWN_PACKAGE)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.packageName).thenReturn(OWN_PACKAGE)
        whenever(mockPackageManager.queryIntentActivities(any(), any<Int>())).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        mockedFileProvider.close()
    }

    @Test
    fun whenFileOpenedThenNewDownloadIsAcknowledged() {
        givenAnExternalViewerIsAvailable()

        assertTrue(testee.openFile(mockContext, file))

        verify(mockNewDownloadState).onNewDownloadAcknowledged()
    }

    @Test
    fun whenViewerCannotBeStartedThenNewDownloadIsNotAcknowledged() {
        givenAnExternalViewerIsAvailable()
        doThrow(ActivityNotFoundException()).whenever(mockContext).startActivity(any())

        assertFalse(testee.openFile(mockContext, file))

        verify(mockNewDownloadState, never()).onNewDownloadAcknowledged()
    }

    @Test
    fun whenNoViewerResolvesThenNewDownloadIsNotAcknowledged() {
        assertFalse(testee.openFile(mockContext, file))

        verify(mockNewDownloadState, never()).onNewDownloadAcknowledged()
    }

    @Test
    fun whenFileSharedThenNewDownloadIsNotAcknowledged() {
        assertTrue(testee.shareFile(mockContext, file))

        verify(mockNewDownloadState, never()).onNewDownloadAcknowledged()
    }

    private fun givenAnExternalViewerIsAvailable() {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = EXTERNAL_PACKAGE
                name = "$EXTERNAL_PACKAGE.ViewerActivity"
                applicationInfo = ApplicationInfo().apply { packageName = EXTERNAL_PACKAGE }
            }
        }
        whenever(mockPackageManager.resolveActivity(any(), any<Int>())).thenReturn(resolveInfo)
    }

    private val file = File("/downloads/$FILE_NAME")

    companion object {
        private const val OWN_PACKAGE = "com.duckduckgo.test"
        private const val EXTERNAL_PACKAGE = "com.example.viewer"
        private const val FILE_NAME = "file.pdf"
    }
}
