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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateDao
import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealEventHubRepositoryTest {

    private val dao: EventHubPixelStateDao = mock()
    private lateinit var repository: RealEventHubRepository

    private val sampleConfig = TelemetryPixelConfig(
        name = "testPixel",
        state = "enabled",
        trigger = TelemetryTriggerConfig(
            period = TelemetryPeriodConfig(days = 1),
        ),
        parameters = mapOf(
            "count" to TelemetryParameterConfig(
                template = "counter",
                source = "adwall.detected",
                buckets = linkedMapOf(
                    "0" to BucketConfig(gte = 0, lt = 1),
                    "1-5" to BucketConfig(gte = 1, lt = 6),
                    "6+" to BucketConfig(gte = 6, lt = null),
                ),
            ),
        ),
    )

    @Before
    fun setUp() {
        repository = RealEventHubRepository(dao)
    }

    @Test
    fun `savePixelState then getPixelState round-trips correctly`() {
        val original = PixelState(
            pixelName = "testPixel",
            periodStartMillis = 1000L,
            periodEndMillis = 87401000L,
            config = sampleConfig,
            params = mapOf("count" to ParamState(value = 3, stopCounting = false)),
        )

        val entityCaptor = argumentCaptor<EventHubPixelStateEntity>()

        repository.savePixelState(original)
        verify(dao).insertPixelState(entityCaptor.capture())

        val savedEntity = entityCaptor.firstValue
        assertEquals("testPixel", savedEntity.pixelName)
        assertEquals(1000L, savedEntity.periodStartMillis)
        assertEquals(87401000L, savedEntity.periodEndMillis)

        whenever(dao.getPixelState("testPixel")).thenReturn(savedEntity)
        val restored = repository.getPixelState("testPixel")

        assertNotNull(restored)
        assertEquals(original.pixelName, restored!!.pixelName)
        assertEquals(original.periodStartMillis, restored.periodStartMillis)
        assertEquals(original.periodEndMillis, restored.periodEndMillis)
        assertEquals(original.params["count"]?.value, restored.params["count"]?.value)
        assertEquals(original.params["count"]?.stopCounting, restored.params["count"]?.stopCounting)
        assertEquals(original.config.name, restored.config.name)
        assertEquals(original.config.trigger.period.days, restored.config.trigger.period.days)
        assertEquals(original.config.parameters.size, restored.config.parameters.size)
    }

    @Test
    fun `savePixelState with stopCounting preserves flag`() {
        val original = PixelState(
            pixelName = "testPixel",
            periodStartMillis = 0L,
            periodEndMillis = 86400000L,
            config = sampleConfig,
            params = mapOf("count" to ParamState(value = 10, stopCounting = true)),
        )

        val entityCaptor = argumentCaptor<EventHubPixelStateEntity>()
        repository.savePixelState(original)
        verify(dao).insertPixelState(entityCaptor.capture())

        whenever(dao.getPixelState("testPixel")).thenReturn(entityCaptor.firstValue)
        val restored = repository.getPixelState("testPixel")

        assertNotNull(restored)
        assertEquals(true, restored!!.params["count"]?.stopCounting)
        assertEquals(10, restored.params["count"]?.value)
    }

    @Test
    fun `getPixelState returns null for missing entry`() {
        whenever(dao.getPixelState("missing")).thenReturn(null)

        assertNull(repository.getPixelState("missing"))
    }

    @Test
    fun `getPixelState returns null for corrupt config JSON`() {
        whenever(dao.getPixelState("corrupt")).thenReturn(
            EventHubPixelStateEntity(
                pixelName = "corrupt",
                periodStartMillis = 0,
                periodEndMillis = 1000,
                paramsJson = """{"count":{"value":1}}""",
                configJson = "not valid json",
            ),
        )

        assertNull(repository.getPixelState("corrupt"))
    }

    @Test
    fun `getAllPixelStates skips entities with corrupt config`() {
        val goodEntity = run {
            val entityCaptor = argumentCaptor<EventHubPixelStateEntity>()
            repository.savePixelState(
                PixelState("good", 0, 86400000, sampleConfig, mapOf("count" to ParamState(0))),
            )
            verify(dao).insertPixelState(entityCaptor.capture())
            entityCaptor.firstValue
        }

        val badEntity = EventHubPixelStateEntity(
            pixelName = "bad",
            periodStartMillis = 0,
            periodEndMillis = 1000,
            paramsJson = "{}",
            configJson = "corrupt",
        )

        whenever(dao.getAllPixelStates()).thenReturn(listOf(goodEntity, badEntity))
        val results = repository.getAllPixelStates()

        assertEquals(1, results.size)
        assertEquals("good", results[0].pixelName)
    }

    @Test
    fun `deletePixelState delegates to dao`() {
        repository.deletePixelState("testPixel")
        verify(dao).deletePixelState("testPixel")
    }

    @Test
    fun `deleteAllPixelStates delegates to dao`() {
        repository.deleteAllPixelStates()
        verify(dao).deleteAllPixelStates()
    }

    @Test
    fun `params serialization handles empty map`() {
        val json = RealEventHubRepository.serializeParams(emptyMap())
        val parsed = RealEventHubRepository.parseParamsJson(json)

        assertEquals(emptyMap<String, ParamState>(), parsed)
    }

    @Test
    fun `params serialization handles multiple parameters`() {
        val original = mapOf(
            "count" to ParamState(value = 5, stopCounting = false),
            "other" to ParamState(value = 0, stopCounting = true),
        )

        val json = RealEventHubRepository.serializeParams(original)
        val parsed = RealEventHubRepository.parseParamsJson(json)

        assertEquals(2, parsed.size)
        assertEquals(5, parsed["count"]?.value)
        assertEquals(false, parsed["count"]?.stopCounting)
        assertEquals(0, parsed["other"]?.value)
        assertEquals(true, parsed["other"]?.stopCounting)
    }

    @Test
    fun `parseParamsJson returns empty map for invalid JSON`() {
        assertEquals(emptyMap<String, ParamState>(), RealEventHubRepository.parseParamsJson("not json"))
    }

    @Test
    fun `savePixelState stores configJson from serializePixelConfig`() {
        val state = PixelState(
            pixelName = "testPixel",
            periodStartMillis = 0L,
            periodEndMillis = 86400000L,
            config = sampleConfig,
            params = mapOf("count" to ParamState(0)),
        )

        val entityCaptor = argumentCaptor<EventHubPixelStateEntity>()
        repository.savePixelState(state)
        verify(dao).insertPixelState(entityCaptor.capture())

        val expectedJson = EventHubConfigParser.serializePixelConfig(sampleConfig)
        assertEquals(expectedJson, entityCaptor.firstValue.configJson)
    }
}
