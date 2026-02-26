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

package com.duckduckgo.app.startup.metrics

import android.app.ActivityManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class MemoryCollectorTest {
    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var collector: MemoryCollector

    @Before
    fun setup() {
        context = mock()
        activityManager = mock()
        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        collector = RealMemoryCollector(context)
    }

    @Test
    fun `when device has less than 1GB RAM then returns less than 1GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 512)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("<1GB", bucket)
    }

    @Test
    fun `when device has 1GB RAM then returns 1GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 1024)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("1GB", bucket)
    }

    @Test
    fun `when device has 1point5GB RAM then returns 1GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 1536)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("1GB", bucket)
    }

    @Test
    fun `when device has 2GB RAM then returns 2GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 2048)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("2GB", bucket)
    }

    @Test
    fun `when device has 3GB RAM then returns 2GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 3072)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("2GB", bucket)
    }

    @Test
    fun `when device has 4GB RAM then returns 4GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 4096)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("4GB", bucket)
    }

    @Test
    fun `when device has 6GB RAM then returns 6GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 6144)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("6GB", bucket)
    }

    @Test
    fun `when device has 8GB RAM then returns 8GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 8192)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("8GB", bucket)
    }

    @Test
    fun `when device has 12GB RAM then returns 12GB bucket`() {
        val memoryInfo = createMemoryInfo(totalRamMb = 12288)
        setupMockMemoryInfo(memoryInfo)

        val bucket = collector.collectDeviceRamBucket()

        assertEquals("12GB", bucket)
    }

    @Test
    fun `when device has 16GB or more RAM then returns 16GB+ bucket`() {
        val memoryInfo16GB = createMemoryInfo(totalRamMb = 16384)
        setupMockMemoryInfo(memoryInfo16GB)
        assertEquals("16GB+", collector.collectDeviceRamBucket())

        val memoryInfo18GB = createMemoryInfo(totalRamMb = 18432)
        setupMockMemoryInfo(memoryInfo18GB)
        assertEquals("16GB+", collector.collectDeviceRamBucket())
    }

    @Test
    fun `when ActivityManager is null then returns null`() {
        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(null)
        collector = RealMemoryCollector(context)

        val bucket = collector.collectDeviceRamBucket()

        assertNull("Bucket should be null when ActivityManager unavailable", bucket)
    }

    @Test
    fun `when getMemoryInfo throws exception then returns null`() {
        doThrow(RuntimeException("Test exception")).whenever(activityManager).getMemoryInfo(any())

        val bucket = collector.collectDeviceRamBucket()

        assertNull("Bucket should be null when getMemoryInfo throws", bucket)
    }

    private fun createMemoryInfo(totalRamMb: Int): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().apply {
            totalMem = totalRamMb * 1024L * 1024L // Convert MB to bytes
            availMem = totalMem / 2 // Not used in bucketing, just set to something
        }
    }

    private fun setupMockMemoryInfo(memoryInfo: ActivityManager.MemoryInfo) {
        doAnswer { invocation ->
            val outInfo = invocation.getArgument<ActivityManager.MemoryInfo>(0)
            outInfo.totalMem = memoryInfo.totalMem
            outInfo.availMem = memoryInfo.availMem
            null
        }.whenever(activityManager).getMemoryInfo(any())
    }
}
