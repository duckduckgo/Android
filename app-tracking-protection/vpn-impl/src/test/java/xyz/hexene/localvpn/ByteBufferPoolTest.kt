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

package xyz.hexene.localvpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ByteBufferPoolTest {

    @Test
    fun whenAcquireThenReturnBuffer() {
        assertNotNull(ByteBufferPool.acquire())
    }

    @Test
    fun whenReleaseNullBufferThenReturn() {
        assertEquals(ByteBufferPool.ENULL, ByteBufferPool.release(null))
    }

    @Test
    fun whenReleaseBufferThenSuccess() {
        assertEquals(0, ByteBufferPool.release(ByteBufferPool.acquire()))
    }

    @Test
    fun whenReleaseSameBufferTwiceThenError() {
        val buffer = ByteBufferPool.acquire()

        assertEquals(0, ByteBufferPool.release(buffer))
        assertEquals(ByteBufferPool.EEXIST, ByteBufferPool.release(buffer))
    }

    @Test
    fun whenAcquireBufferThenIncrementAllocationCount() {
        assertEquals(0, ByteBufferPool.allocations.get())

        // new alloc, increment count
        ByteBufferPool.acquire()
        assertEquals(1, ByteBufferPool.allocations.get())

        // new alloc, increment count
        val b = ByteBufferPool.acquire()
        assertEquals(2, ByteBufferPool.allocations.get())

        // release, buffer back to pool
        ByteBufferPool.release(b)
        assertEquals(1, ByteBufferPool.allocations.get())

        // new alloc, no count increment because poll should have one element
        ByteBufferPool.acquire()
        assertEquals(1, ByteBufferPool.allocations.get())
    }
}
