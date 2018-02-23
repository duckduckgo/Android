/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.net.Uri
import com.duckduckgo.app.global.AppUrl.ParamKey
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DuckDuckGoRequestRewriterTest {

    private lateinit var testee: DuckDuckGoRequestRewriter
    private var mockStatisticsStore: StatisticsDataStore = mock()
    private lateinit var builder: Uri.Builder

    @Before
    fun before() {
        testee = DuckDuckGoRequestRewriter(DuckDuckGoUrlDetector(), mockStatisticsStore)
        builder = Uri.Builder()
    }

    @Test
    fun whenAddingCustomParamsSourceParameterIsAdded() {
        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.SOURCE))
        assertEquals("ddg_android", uri.getQueryParameter(ParamKey.SOURCE))
    }

    @Test
    fun whenAddingCustomParamsAppVersionParameterIsAdded() {
        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.APP_VERSION))
        assertEquals("android_${BuildConfig.VERSION_NAME.replace(".", "_")}", uri.getQueryParameter("tappv"))
    }

    @Test
    fun whenAddingCustomParamsIfStoreContainsAtbParametersThenTheyAreAdded() {
        whenever(mockStatisticsStore.atb).thenReturn("v105-2ma")
        whenever(mockStatisticsStore.retentionAtb).thenReturn("v105-3")

        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertTrue(uri.queryParameterNames.contains(ParamKey.ATB))
        assertTrue(uri.queryParameterNames.contains(ParamKey.RETENTION_ATB))
        assertEquals("v105-2ma", uri.getQueryParameter(ParamKey.ATB))
        assertEquals("v105-3", uri.getQueryParameter(ParamKey.RETENTION_ATB))
    }

    @Test
    fun whenAddingCustomParamsIfIsStoreMissingAtbThenNeitherAtbOrRetentionAtbAdded() {
        whenever(mockStatisticsStore.atb).thenReturn(null)
        whenever(mockStatisticsStore.retentionAtb).thenReturn("v105-3")

        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.ATB))
        assertFalse(uri.queryParameterNames.contains(ParamKey.RETENTION_ATB))
    }

    @Test
    fun whenAddingCustomParamsIfStoreMissingRetentionAtbThenNeitherAtbOrRetentionAtbAdded() {
        whenever(mockStatisticsStore.atb).thenReturn("v105-2ma")
        whenever(mockStatisticsStore.retentionAtb).thenReturn(null)

        testee.addCustomQueryParams(builder)
        val uri = builder.build()
        assertFalse(uri.queryParameterNames.contains(ParamKey.ATB))
        assertFalse(uri.queryParameterNames.contains(ParamKey.RETENTION_ATB))
    }

}