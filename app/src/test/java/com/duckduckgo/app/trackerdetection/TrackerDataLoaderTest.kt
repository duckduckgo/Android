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

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.pixels.remoteconfig.OptimizeTrackerEvaluationRCWrapper
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.api.TdsJsonEntity
import com.duckduckgo.app.trackerdetection.api.TdsJsonTracker
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.app.trackerdetection.db.TdsTrackerDao
import com.duckduckgo.app.trackerdetection.model.TdsMetadata
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.store.BinaryDataStore
import com.squareup.moshi.Moshi
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class TrackerDataLoaderTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: TrackerDataLoader

    private val mockTrackerDetector: TrackerDetector = mock()
    private val mockTdsTrackerDao: TdsTrackerDao = mock()
    private val mockTdsEntityDao: TdsEntityDao = mock()
    private val mockTdsDomainEntityDao: TdsDomainEntityDao = mock()
    private val mockTdsCnameEntityDao: TdsCnameEntityDao = mock()
    private val mockTdsMetadataDao: TdsMetadataDao = mock()
    private val mockContext: Context = mock()
    private val mockAppDatabase: AppDatabase = mock()
    private val mockUrlToTypeMapper: UrlToTypeMapper = mock()

    private val runnableCaptor = argumentCaptor<Runnable>()
    private val tdsMetaDataCaptor = argumentCaptor<TdsMetadata>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val binaryDataStore = BinaryDataStore(context)

    @Before
    fun setup() {
        testee = TrackerDataLoader(
            appCoroutineScope = TestScope(),
            trackerDetector = mockTrackerDetector,
            tdsTrackerDao = mockTdsTrackerDao,
            tdsEntityDao = mockTdsEntityDao,
            tdsDomainEntityDao = mockTdsDomainEntityDao,
            tdsCnameEntityDao = mockTdsCnameEntityDao,
            tdsMetadataDao = mockTdsMetadataDao,
            context = mockContext,
            appDatabase = mockAppDatabase,
            moshi = Moshi.Builder().build(),
            urlToTypeMapper = mockUrlToTypeMapper,
            coroutineRule.testDispatcherProvider,
            object : OptimizeTrackerEvaluationRCWrapper {
                override val enabled: Boolean
                    get() = false
            },
            binaryDataStore = binaryDataStore,
        )
    }

    @Test
    fun whenPersistTdsThenPersistEntities() {
        val tdsJson = TdsJson()

        tdsJson.cnames = mapOf(Pair("host.com", "uncloaked-host.com"))
        tdsJson.trackers = mapOf(Pair("tracker", TdsJsonTracker(null, null, null, null, null)))
        tdsJson.domains = mapOf(Pair("domain.com", "Domain"))
        tdsJson.entities = mapOf(Pair("entity", TdsJsonEntity(null, 1.0)))
        tdsJson.cpm = mapOf(
            Pair(
                "filterlist",
                "[Adblock Plus 2.0]\\n! Checksum: S/aapWujS4hFY0H2a84i+Q\\n! Title: Easylist Cookie List\\n! Updated: 2024-07-05 13:15 UTC\\n! " +
                    "Expires: 9 days (update frequency)\\n! License: http://creativecommons.org/licenses/by/3.0/\\n! Please report any unblocked " +
                    "content or problems by email or in our forums\\n! Email: easylist@protonmail.com \\n! Homepage: https://easylist.to/\\n! " +
                    "Forums: https://forums.lanik.us/\\n!\\n!....",
            ),
        )

        testee.persistTds("eTag", tdsJson)

        verify(mockAppDatabase).runInTransaction(runnableCaptor.capture())
        runnableCaptor.firstValue.run()

        verify(mockTdsMetadataDao).tdsDownloadSuccessful(tdsMetaDataCaptor.capture())
        assertEquals("eTag", tdsMetaDataCaptor.firstValue.eTag)

        verify(mockTdsDomainEntityDao).updateAll(tdsJson.jsonToDomainEntities())
        verify(mockTdsTrackerDao).updateAll(tdsJson.jsonToTrackers().values)
        verify(mockTdsCnameEntityDao).updateAll(tdsJson.jsonToCnameEntities())
    }
}
