/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.integration

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.store.BinaryDataStore
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.httpsupgrade.impl.HttpsBloomFilterFactoryImpl
import com.duckduckgo.httpsupgrade.impl.HttpsDataPersister
import com.duckduckgo.httpsupgrade.impl.HttpsFalsePositivesJsonAdapter
import com.duckduckgo.httpsupgrade.impl.HttpsUpgraderImpl
import com.duckduckgo.httpsupgrade.store.HttpsUpgradeDatabase
import com.duckduckgo.httpsupgrade.store.PlayHttpsEmbeddedDataPersister
import com.duckduckgo.privacy.config.api.Https
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HttpsEmbeddedDataIntegrationTest {

    private lateinit var httpsUpgrader: HttpsUpgrader
    private lateinit var db: HttpsUpgradeDatabase

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var moshi = Moshi.Builder().add(HttpsFalsePositivesJsonAdapter()).build()
    private var mockFeatureToggle: FeatureToggle = mock()
    private var mockHttps: Https = mock()
    private var mockPixel: Pixel = mock()

    @Before
    fun before() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName.value)).thenReturn(true)

        db = Room.inMemoryDatabaseBuilder(context, HttpsUpgradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val httpsBloomSpecDao = db.httpsBloomFilterSpecDao()
        val httpsFalsePositivesDao = db.httpsFalsePositivesDao()
        val binaryDataStore = BinaryDataStore(context)

        val persister = HttpsDataPersister(
            binaryDataStore,
            httpsBloomSpecDao,
            httpsFalsePositivesDao,
            db,
        )

        val embeddedDataPersister = PlayHttpsEmbeddedDataPersister(persister, binaryDataStore, httpsBloomSpecDao, context, moshi)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factory = HttpsBloomFilterFactoryImpl(httpsBloomSpecDao, binaryDataStore, embeddedDataPersister, persister, mockPixel, context)
        httpsUpgrader = HttpsUpgraderImpl(factory, httpsFalsePositivesDao, mockFeatureToggle, mockHttps)
        httpsUpgrader.reloadData()
    }

    @After
    fun after() {
        db.close()
    }

    @Ignore("This test is ignored because it has been flaky lately, see https://app.asana.com/0/1202552961248957/1203035277827863/f")
    fun whenUpgraderLoadedWithEmbeddedDataAndItemInBloomListThenUpdgraded() {
        assertTrue(httpsUpgrader.shouldUpgrade(Uri.parse("http://facebook.com")))
    }

    @Test
    fun whenUpgraderLoadedWithEmbeddedDataAndItemInFalsePositivesListThenNotUpdgraded() {
        assertFalse(httpsUpgrader.shouldUpgrade(Uri.parse("http://www.dppps.sc.gov")))
    }

    @Test
    fun whenUpgraderLoadedWithEmbeddedDataAndItemInBloomListThenNotUpdgraded() {
        assertFalse(httpsUpgrader.shouldUpgrade(Uri.parse("http://fjdsfhdksjfhdsfhdjsfhdsjfhdsjkfhdsjja.com")))
    }
}
