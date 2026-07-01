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

package com.duckduckgo.downloads.impl.location

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.downloads.api.CustomDownloadLocation
import com.duckduckgo.downloads.api.DownloadDestination
import com.duckduckgo.downloads.api.DownloadLocationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDownloadDestinationResolverTest {

    private val downloadLocationRepository: DownloadLocationRepository = mock()
    private val safDownloadStorage: SafDownloadStorage = mock()
    private val resolver = RealDownloadDestinationResolver(downloadLocationRepository, safDownloadStorage)

    @Test
    fun whenNoCustomLocationConfiguredThenDefaultDestinationReturned() = runTest {
        whenever(downloadLocationRepository.getCustomLocation()).thenReturn(null)

        val result = resolver.resolve()

        assertEquals(DownloadDestination.Default, result.destination)
        assertFalse(result.usedFallback)
    }

    @Test
    fun whenCustomLocationAccessibleThenCustomDestinationReturned() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        whenever(downloadLocationRepository.getCustomLocation()).thenReturn(
            CustomDownloadLocation(treeUri = treeUri, displayName = "Download", pathLabel = "Downloads"),
        )
        whenever(safDownloadStorage.isTreeAccessible(any<Uri>())).thenReturn(true)

        val result = resolver.resolve()

        assertTrue(result.destination is DownloadDestination.CustomTree)
        assertEquals(treeUri, (result.destination as DownloadDestination.CustomTree).treeUri)
        assertFalse(result.usedFallback)
    }

    @Test
    fun whenCustomLocationInaccessibleThenFallbackToDefault() = runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        whenever(downloadLocationRepository.getCustomLocation()).thenReturn(
            CustomDownloadLocation(treeUri = treeUri, displayName = "Download", pathLabel = "Downloads"),
        )
        whenever(safDownloadStorage.isTreeAccessible(any<Uri>())).thenReturn(false)

        val result = resolver.resolve()

        assertEquals(DownloadDestination.Default, result.destination)
        assertTrue(result.usedFallback)
    }
}
