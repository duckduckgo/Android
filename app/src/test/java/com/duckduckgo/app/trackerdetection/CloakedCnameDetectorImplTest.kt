/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.flags.OptimizeCnameDetectionRCWrapper
import com.duckduckgo.app.trackerdetection.model.TdsCnameEntity
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CloakedCnameDetectorImplTest {

    private lateinit var testee: CloakedCnameDetector
    private val mockCnameEntityDao: TdsCnameEntityDao = mock()
    private val mockTrackerAllowList: TrackerAllowlist = mock()
    private val mockUri: Uri = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockOptimizeCnameDetectionRCWrapper: OptimizeCnameDetectionRCWrapper = mock()

    @Before
    fun setup() {
        whenever(mockOptimizeCnameDetectionRCWrapper.enabled).thenReturn(true)
        testee = CloakedCnameDetectorImpl(
            mockCnameEntityDao,
            mockTrackerAllowList,
            mockUserAllowListRepository,
            mockOptimizeCnameDetectionRCWrapper,
        )
    }

    @Test
    fun whenDetectCnameAndHostIsNullThenReturnNull() {
        whenever(mockUri.host).thenReturn(null)
        assertNull(testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedThenReturnUncloakedHost() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        assertEquals("http://uncloaked-host.com", testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedAndHasSchemeThenReturnUncloakedHostWithScheme() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockUri.scheme).thenReturn("https")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        assertEquals("https://uncloaked-host.com", testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameNotDetectedThenReturnNull() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(emptyList())
        assertEquals(null, testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedAndHasPathThenReturnUncloakedHostWithPathAppended() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockUri.path).thenReturn("/path")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        assertEquals("http://uncloaked-host.com/path", testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedAndHasSchemeAndPathThenReturnUncloakedHostWithSchemeAndPathAppended() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockUri.path).thenReturn("/path")
        whenever(mockUri.scheme).thenReturn("https")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        assertEquals("https://uncloaked-host.com/path", testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenRequestUrlIsInAllowListThenReturnNull() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        whenever(mockTrackerAllowList.isAnException(anyString(), anyString())).thenReturn(true)
        assertEquals(null, testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenDetectCnameCloakedHostAndUrlIsInUserAllowListThenReturnNull() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        whenever(mockUserAllowListRepository.isUriInUserAllowList(any())).thenReturn(true)
        assertNull(testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenHostIsNotCloakedThenAllowlistsAreNotEvaluated() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("other-host.com", "uncloaked-host.com")))

        assertNull(testee.detectCnameCloakedHost("foo.com", mockUri))

        verifyNoInteractions(mockTrackerAllowList)
        verifyNoInteractions(mockUserAllowListRepository)
    }

    @Test
    fun whenDetectCnameRepeatedlyThenCnameTableIsReadOnce() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))

        testee.detectCnameCloakedHost("foo.com", mockUri)
        testee.detectCnameCloakedHost("foo.com", mockUri)

        verify(mockCnameEntityDao).getAll()
        verify(mockCnameEntityDao, never()).get(anyString())
    }

    @Test
    fun whenRefreshedThenSubsequentDetectionUsesTheNewCnameTable() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.getAll()).thenReturn(emptyList())
        assertNull(testee.detectCnameCloakedHost("foo.com", mockUri))

        whenever(mockCnameEntityDao.getAll()).thenReturn(listOf(TdsCnameEntity("host.com", "uncloaked-host.com")))
        (testee as CloakedCnameRefresher).refresh()

        assertEquals("http://uncloaked-host.com", testee.detectCnameCloakedHost("foo.com", mockUri))
    }

    @Test
    fun whenCacheIsDisabledThenCnameIsResolvedFromTheDao() {
        whenever(mockOptimizeCnameDetectionRCWrapper.enabled).thenReturn(false)
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.get(any())).thenReturn(TdsCnameEntity("host.com", "uncloaked-host.com"))

        assertEquals("http://uncloaked-host.com", testee.detectCnameCloakedHost("foo.com", mockUri))

        verify(mockCnameEntityDao, never()).getAll()
    }
}
