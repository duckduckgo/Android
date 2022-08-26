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
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.model.TdsCnameEntity
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CloakedCnameDetectorImplTest {

    private lateinit var testee: CloakedCnameDetector
    private val mockCnameEntityDao: TdsCnameEntityDao = mock()
    private val mockUri: Uri = mock()

    @Before
    fun setup() {
        testee = CloakedCnameDetectorImpl(mockCnameEntityDao)
    }

    @Test
    fun whenDetectCnameAndHostIsNullThenReturnNull() {
        whenever(mockUri.host).thenReturn(null)
        assertNull(testee.detectCnameCloakedHost(mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedThenReturnUncloakedHost() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.get(any())).thenReturn(TdsCnameEntity("host.com", "uncloaked-host.com"))
        assertEquals("uncloaked-host.com", testee.detectCnameCloakedHost(mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameNotDetectedThenReturnNull() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockCnameEntityDao.get(any())).thenReturn(null)
        assertEquals(null, testee.detectCnameCloakedHost(mockUri))
    }

    @Test
    fun whenDetectCnameAndCnameDetectedAndHasPathThenReturnUncloakedHostWithPathAppended() {
        whenever(mockUri.host).thenReturn("host.com")
        whenever(mockUri.path).thenReturn("/path")
        whenever(mockCnameEntityDao.get(any())).thenReturn(TdsCnameEntity("host.com", "uncloaked-host.com"))
        assertEquals("uncloaked-host.com/path", testee.detectCnameCloakedHost(mockUri))
    }
}
