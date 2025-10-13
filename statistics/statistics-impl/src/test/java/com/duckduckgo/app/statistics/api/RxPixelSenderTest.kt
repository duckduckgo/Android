/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult.PIXEL_IGNORED
import com.duckduckgo.app.statistics.api.PixelSender.SendPixelResult.PIXEL_SENT
import com.duckduckgo.app.statistics.api.RxPixelSenderTest.TestPixels.TEST
import com.duckduckgo.app.statistics.config.StatisticsLibraryConfig
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.model.QueryParamsTypeConverter
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.statistics.store.PixelFiredRepository
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.device.DeviceInfo
import io.reactivex.Completable
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.kotlin.*
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class RxPixelSenderTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @Mock
    val api: PixelService = mock()

    @Mock
    val mockStatisticsDataStore: StatisticsDataStore = mock()

    @Mock
    val mockDeviceInfo: DeviceInfo = mock()

    private lateinit var db: TestAppDatabase
    private lateinit var pendingPixelDao: PendingPixelDao
    private lateinit var testee: RxPixelSender
    private val mockLifecycleOwner: LifecycleOwner = mock()
    private val pixelFiredRepository = FakePixelFiredRepository()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TestAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pendingPixelDao = db.pixelDao()

        testee = RxPixelSender(
            api,
            pendingPixelDao,
            mockStatisticsDataStore,
            mockDeviceInfo,
            object : StatisticsLibraryConfig {
                override fun shouldFirePixelsAsDev() = true
            },
            pixelFiredRepository,
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
        givenVariant("variant")
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Count)
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq("test"), eq("phone"), eq("atbvariant"), any(), any(), any())
    }

    @Test
    fun whenPixelFiredTabletFormFactorThenPixelServiceCalledWithTabletParameter() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.TABLET)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Count)
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq("test"), eq("tablet"), eq(""), any(), any(), any())
    }

    @Test
    fun whenPixelFiredWithNoAtbThenPixelServiceCalledWithCorrectPixelNameAndNoAtb() {
        givenApiSendPixelSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Count)
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq("test"), eq("phone"), eq(""), any(), any(), any())
    }

    @Test
    fun whenPixelFiredWithAdditionalParametersThenPixelServiceCalledWithDefaultAndAdditionalParameters() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant("variant")
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        val params = mapOf("param1" to "value1", "param2" to "value2")
        val expectedParams = mapOf("param1" to "value1", "param2" to "value2", "appVersion" to "1.0.0")
        testee.sendPixel(TEST.pixelName, params, emptyMap(), Count)
            .test().assertValue(PIXEL_SENT)

        verify(api).fire("test", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelFiredWithoutAdditionalParametersThenPixelServiceCalledWithOnlyDefaultParameters() {
        givenPixelApiSucceeds()
        givenAtbVariant(Atb("atb"))
        givenVariant("variant")
        givenFormFactor(DeviceInfo.FormFactor.PHONE)
        givenAppVersion("1.0.0")

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Count)
            .test().assertValue(PIXEL_SENT)

        val expectedParams = mapOf("appVersion" to "1.0.0")
        verify(api).fire("test", "phone", "atbvariant", expectedParams, emptyMap())
    }

    @Test
    fun whenPixelEnqueuedWitAdditionalParametersThenPixelEnqueuedWithParameters() {
        givenAtbVariant(Atb("atb"))
        givenVariant("variant")
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
        givenVariant("variant")
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

    @Test
    fun whenDailyPixelIsFiredThenPixelNameIsStored() = runTest {
        givenPixelApiSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily())
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertTrue(TEST.pixelName in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenDailyPixelFireFailsThenPixelNameIsNotStored() = runTest {
        givenPixelApiFails()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily())
            .test().assertError(RuntimeException::class.java)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertFalse(TEST.pixelName in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenDailyPixelHasAlreadyBeenFiredTodayThenItIsNotFiredAgain() = runTest {
        pixelFiredRepository.dailyPixelsFiredToday += TEST.pixelName

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily())
            .test().assertValue(PIXEL_IGNORED)

        verifyNoInteractions(api)
        assertTrue(TEST.pixelName in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenDailyPixelIsFiredThenTagIsStored() = runTest {
        givenPixelApiSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily("tag"))
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertTrue("tag" in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenDailyPixelFireFailsThenTagIsNotStored() = runTest {
        givenPixelApiFails()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily("tag"))
            .test().assertError(RuntimeException::class.java)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertFalse("tag" in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenDailyPixelHasAlreadyBeenFiredAndUsesTagTodayThenItIsNotFiredAgain() = runTest {
        pixelFiredRepository.dailyPixelsFiredToday += "tag"

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Daily("tag"))
            .test().assertValue(PIXEL_IGNORED)

        verifyNoInteractions(api)
        assertTrue("tag" in pixelFiredRepository.dailyPixelsFiredToday)
    }

    @Test
    fun whenUniquePixelIsFiredThenPixelNameIsStored() = runTest {
        givenPixelApiSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique())
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertTrue(TEST.pixelName in pixelFiredRepository.uniquePixelsFired)
    }

    @Test
    fun whenUniquePixelFireFailsThenPixelNameIsNotStored() = runTest {
        givenPixelApiFails()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique())
            .test().assertError(RuntimeException::class.java)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertFalse(TEST.pixelName in pixelFiredRepository.uniquePixelsFired)
    }

    @Test
    fun whenUniquePixelHasAlreadyBeenFiredThenItIsNotFiredAgain() = runTest {
        pixelFiredRepository.uniquePixelsFired += TEST.pixelName

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique())
            .test().assertValue(PIXEL_IGNORED)

        verifyNoInteractions(api)
        assertTrue(TEST.pixelName in pixelFiredRepository.uniquePixelsFired)
    }

    @Test
    fun whenUniquePixelIsFiredThenTagIsStored() = runTest {
        givenPixelApiSucceeds()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique("tag"))
            .test().assertValue(PIXEL_SENT)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertTrue("tag" in pixelFiredRepository.uniquePixelsFired)
    }

    @Test
    fun whenUniquePixelFireFailsThenTagIsNotStored() = runTest {
        givenPixelApiFails()
        givenFormFactor(DeviceInfo.FormFactor.PHONE)

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique("tag"))
            .test().assertError(RuntimeException::class.java)

        verify(api).fire(eq(TEST.pixelName), any(), any(), any(), any(), any())
        assertFalse("tag" in pixelFiredRepository.uniquePixelsFired)
    }

    @Test
    fun whenUniquePixelHasAlreadyBeenFiredAndUsesTagThenItIsNotFiredAgain() = runTest {
        pixelFiredRepository.uniquePixelsFired += "tag"

        testee.sendPixel(TEST.pixelName, emptyMap(), emptyMap(), Unique("tag"))
            .test().assertValue(PIXEL_IGNORED)

        verifyNoInteractions(api)
        assertTrue("tag" in pixelFiredRepository.uniquePixelsFired)
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

    private fun givenVariant(variantKey: String) {
        whenever(mockStatisticsDataStore.variant).thenReturn(variantKey)
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

@Database(
    exportSchema = false,
    version = 1,
    entities = [PixelEntity::class],
)
@TypeConverters(
    QueryParamsTypeConverter::class,
)
private abstract class TestAppDatabase : RoomDatabase() {
    abstract fun pixelDao(): PendingPixelDao
}

private class FakePixelFiredRepository : PixelFiredRepository {

    val dailyPixelsFiredToday = mutableSetOf<String>()
    val uniquePixelsFired = mutableSetOf<String>()

    override suspend fun storeDailyPixelFiredToday(name: String) {
        dailyPixelsFiredToday += name
    }

    override suspend fun hasDailyPixelFiredToday(name: String): Boolean =
        name in dailyPixelsFiredToday

    override suspend fun storeUniquePixelFired(name: String) {
        uniquePixelsFired += name
    }

    override suspend fun hasUniquePixelFired(name: String): Boolean =
        name in uniquePixelsFired
}
