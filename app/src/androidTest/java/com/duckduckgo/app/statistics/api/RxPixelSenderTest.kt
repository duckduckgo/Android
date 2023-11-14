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
import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.RxPixelSenderTest.TestPixels.TEST
import com.duckduckgo.app.statistics.config.StatisticsLibraryConfig
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.device.DeviceInfo
import io.reactivex.Completable
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.*

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
    private lateinit var pendingPixelDao: PendingPixelDao
    private lateinit var testee: RxPixelSender
    private val mockLifecycleOwner: LifecycleOwner = mock()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pendingPixelDao = db.pixelDao()

        testee = RxPixelSender(
            api,
            pendingPixelDao,
            mockStatisticsDataStore,
            mockVariantManager,
            mockDeviceInfo,
            object : StatisticsLibraryConfig {
                override fun shouldFirePixelsAsDev() = true
            },
        )
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

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("test"), eq("phone"), eq("atbvariant"), any(), any(), any())
    }

    @Test
    fun whenPixelFiredTabletFormFactorThenPixelServiceCalledWithTabletParameter() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.TABLET)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("test"), eq("tablet"), eq(""), any(), any(), any())
    }

    @Test
    fun whenPixelFiredWithNoAtbThenPixelServiceCalledWithCorrectPixelNameAndNoAtb() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap())

        verify(api).fire(eq("test"), eq("phone"), eq(""), any(), any(), any())
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
        testee.sendPixel(TEST.pixelName, params, emptyMap())

        verify(api).fire("test", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelFiredWithoutAdditionalParametersThenPixelServiceCalledWithOnlyDefaultParameters() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap())

        val expectedParams = mapOf("appVersion" to "1.0.0")
        verify(api).fire("test", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelEnqueuedWitAdditionalParametersThenPixelEnqueuedWithParameters() {
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")
        val params = mapOf("param1" to "value1", "param2" to "value2")

        testee.enqueuePixel(TEST.pixelName, params, emptyMap()).test()

        val testObserver = pendingPixelDao.pixels().test()
        val pixels = testObserver.assertNoErrors().values().last()

        assertEquals(1, pixels.size)
        assertPixelEntity(
            PixelEntity(
                pixelName = "test",
                atb = "atbvariant",
                additionalQueryParams = params + mapOf("appVersion" to "1.0.0"),
                encodedQueryParams = emptyMap(),
            ),
            pixels.first(),
        )
    }

    @Test
    fun whenPixelEnqueuedWithoutAdditionalParametersThenPixelEnqueuedWithOnlyDefaultParameters() {
        givenAtbVariant(Atb("atb"))
        givenVariant(Variant("variant", filterBy = { true }))
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        testee.enqueuePixel(TEST.pixelName, emptyMap(), emptyMap()).test()

        val pixels = pendingPixelDao.pixels().test().assertNoErrors().values().last()
        assertEquals(1, pixels.size)
        assertPixelEntity(
            PixelEntity(
                pixelName = "test",
                atb = "atbvariant",
                additionalQueryParams = mapOf("appVersion" to "1.0.0"),
                encodedQueryParams = emptyMap(),
            ),
            pixels.first(),
        )
    }

    @Test
    fun whenAppForegroundedThenPixelSent() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "test",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap(),
        )
        pendingPixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onStart(mockLifecycleOwner)

        verify(api).fire(
            pixelEntity.pixelName,
            "phone",
            pixelEntity.atb,
            pixelEntity.additionalQueryParams,
            pixelEntity.encodedQueryParams,
        )
    }

    @Test
    fun whenAppForegroundedAndPixelSentThenPixelRemoved() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "test",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap(),
        )
        pendingPixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onStart(mockLifecycleOwner)

        val pixels = pendingPixelDao.pixels().test().assertNoErrors().values().last()
        assertTrue(pixels.isEmpty())
    }

    @Test
    fun whenAppForegroundedAndSendPixelFailsThenPixelNotRemoved() {
        givenPixelApiFails()
        val pixelEntity = PixelEntity(
            pixelName = "test",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap(),
        )
        pendingPixelDao.insert(pixelEntity)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onStart(mockLifecycleOwner)

        val testObserver = pendingPixelDao.pixels().test()
        val pixels = testObserver.assertNoErrors().values().last()
        assertTrue(pixels.isNotEmpty())
    }

    @Test
    fun whenAppForegroundedWithMultiplePixelsEnqueuedThenSendAllPixels() {
        givenPixelApiSucceeds()
        val pixelEntity = PixelEntity(
            pixelName = "test",
            atb = "atbvariant",
            additionalQueryParams = mapOf("appVersion" to "1.0.0"),
            encodedQueryParams = emptyMap(),
        )
        pendingPixelDao.insert(pixelEntity, times = 5)
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.onStart(mockLifecycleOwner)

        verify(api, times(5)).fire(
            pixelEntity.pixelName,
            "phone",
            pixelEntity.atb,
            pixelEntity.additionalQueryParams,
            pixelEntity.encodedQueryParams,
        )
    }

    private fun assertPixelEntity(
        expectedEntity: PixelEntity,
        pixelEntity: PixelEntity,
    ) {
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

    private fun PendingPixelDao.insert(
        pixel: PixelEntity,
        times: Int,
    ) {
        for (x in 1..times) {
            this.insert(pixel)
        }
    }

    enum class TestPixels(override val pixelName: String, val enqueue: Boolean = false) : Pixel.PixelName {
        TEST("test"),
    }
}
