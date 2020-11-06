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

package com.duckduckgo.app.statistics.api

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.app.statistics.store.PixelDao
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Completable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import java.util.concurrent.TimeoutException

class RxPixelSenderTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    val api: PixelService = mock()

    @Mock
    val mockStatisticsDataStore: StatisticsDataStore = mock()

    @Mock
    val mockVariantManager: VariantManager = mock()

    @Mock
    val mockDeviceInfo: DeviceInfo = mock()

    private lateinit var db: AppDatabase
    private lateinit var pixelDao: PixelDao
    private lateinit var testee: RxPixelSender

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pixelDao = db.pixelDao()

        testee = RxPixelSender(api, pixelDao, mockStatisticsDataStore, mockVariantManager, mockDeviceInfo)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenPixelFiredThenPixelServiceCalledWithCorrectAtbAndVariant() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(PRIVACY_DASHBOARD_OPENED.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("mp"), eq("phone"), eq("atbvariant"), any(), any(), any())
    }

    @Test
    fun whenPixelFiredTabletFormFactorThenPixelServiceCalledWithTabletParameter() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.TABLET)

        testee.sendPixel(PRIVACY_DASHBOARD_OPENED.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("mp"), eq("tablet"), eq(""), any(), any(), any())
    }

    @Test
    fun whenPixelFiredWithNoAtbThenPixelServiceCalledWithCorrectPixelNameAndNoAtb() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(PRIVACY_DASHBOARD_OPENED.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("mp"), eq("phone"), eq(""), any(), any(), any())
    }

    @Test
    fun whenPixelFiredWithAdditionalParametersThenPixelServiceCalledWithDefaultAndAdditionalParameters() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        val params = mapOf("param1" to "value1", "param2" to "value2")
        val expectedParams = mapOf("param1" to "value1", "param2" to "value2", "appVersion" to "1.0.0")
        testee.sendPixel(PRIVACY_DASHBOARD_OPENED.pixelName, params, emptyMap())

        verify(api).fire("mp", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelFiredWithoutAdditionalParametersThenPixelServiceCalledWithOnlyDefaultParameters() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        testee.sendPixel(PRIVACY_DASHBOARD_OPENED.pixelName, emptyMap(), emptyMap())

        val expectedParams = mapOf("appVersion" to "1.0.0")
        verify(api).fire("mp", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelEnqueuedWitAdditionalParametersThenPixelEnqueuedWithParameters() {
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")
        val params = mapOf("param1" to "value1", "param2" to "value2")

        testee.enqueuePixel(PRIVACY_DASHBOARD_OPENED.pixelName, params, emptyMap())

        pixelDao.unsentPixels().subscribe {
            assertEquals(1, it.size)
            assertPixelEntity(
                PixelEntity(
                    pixelName = "mp",
                    atb = "atbvariant",
                    additionalQueryParams = params + mapOf("appVersion" to "1.0.0"),
                    encodedQueryParams = emptyMap()
                ),
                it.first()
            )
        }
    }

    @Test
    fun whenPixelEnqueuedWithoutAdditionalParametersThenPixelEnqueuedWithOnlyDefaultParameters() {
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        testee.enqueuePixel(PRIVACY_DASHBOARD_OPENED.pixelName, emptyMap(), emptyMap())

        pixelDao.unsentPixels().subscribe {
            assertEquals(1, it.size)
            assertPixelEntity(
                PixelEntity(
                    pixelName = "mp",
                    atb = "atbvariant",
                    additionalQueryParams = mapOf("appVersion" to "1.0.0"),
                    encodedQueryParams = emptyMap()
                ),
                it.first()
            )
        }
    }

    @Test
    fun whenAppForegroundedThenPixelSent() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "mp",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap()
        )
        pixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onAppForegrounded()

        verify(api).fire(
            pixelEntity.pixelName, "phone", pixelEntity.atb, pixelEntity.additionalQueryParams, pixelEntity.encodedQueryParams
        )
    }

    @Test
    fun whenAppForegroundedAndPixelSentThenPixelRemoved() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "mp",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap()
        )
        pixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onAppForegrounded()

        pixelDao.unsentPixels().subscribe {
            assertTrue(it.isEmpty())
        }
    }

    @Test
    fun whenAppForegroundedAndSendPixelFailsThenPixelNotRemoved() {
        givenPixelApiFails()
        val pixelEntity = PixelEntity(
            pixelName = "mp",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap()
        )
        pixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onAppForegrounded()

        pixelDao.unsentPixels().subscribe {
            assertTrue(it.isNotEmpty())
        }
    }

    @Test
    fun whenAppForegroundedWithMultiplePixelsEnqueuedThenSendAllPixels() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "mp",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap()
        )
        pixelDao.insert(pixelEntity, times = 5)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onAppForegrounded()

        verify(api, times(5)).fire(
            pixelEntity.pixelName, "phone", pixelEntity.atb, pixelEntity.additionalQueryParams, pixelEntity.encodedQueryParams
        )
    }

    private fun assertPixelEntity(expectedEntity: PixelEntity, pixelEntity: PixelEntity) {
        assertEquals(expectedEntity.pixelName, pixelEntity.pixelName)
        assertEquals(expectedEntity.atb, pixelEntity.atb)
        assertEquals(expectedEntity.additionalQueryParams, pixelEntity.additionalQueryParams)
        assertEquals(expectedEntity.encodedQueryParams, pixelEntity.encodedQueryParams)
    }

    @Suppress("SameParameterValue")
    private fun givenAppVersion(appVersion: String) {
        whenever(mockDeviceInfo.appVersion).thenReturn(appVersion)
    }

    private fun givenApiSendPixelSucceeds() {
        whenever(api.fire(any(), any(), any(), any(), any(), any())).thenReturn(Completable.complete())
    }

    private fun givenVariant(variant: Variant) {
        whenever(mockVariantManager.getVariant()).thenReturn(variant)
    }

    private fun givenAtbVariant(atb: Atb) {
        whenever(mockStatisticsDataStore.atb).thenReturn(atb)
    }

    private fun givenFormFactor(formFactor: DeviceInfo.FormFactor) {
        whenever(mockDeviceInfo.formFactor()).thenReturn(formFactor)
    }

    private fun givenPixelApiSucceeds() {
        whenever(api.fire(any(), any(), any(), anyOrNull(), any(), any())).thenReturn(Completable.complete())
    }

    private fun givenPixelApiFails() {
        whenever(api.fire(any(), any(), any(), anyOrNull(), any(), any())).thenReturn(Completable.error(TimeoutException()))
    }

    private fun PixelDao.insert(pixel: PixelEntity, times: Int) {
        for (x in 0..times) {
            this.insert(pixel)
        }
    }
}
