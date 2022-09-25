/*
 ** Copyright 2015, Mohamed Naufal
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

package xyz.hexene.localvpn;

import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import kotlin.jvm.Synchronized;
import timber.log.Timber;

public class ByteBufferPool {
    static final int BUFFER_SIZE = 16384; // XXX: Is this ideal?
    private static final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private static final HashSet<Integer> poolKeys = new HashSet<>();
    public static final AtomicLong allocations = new AtomicLong();

    @Synchronized
    public static ByteBuffer acquire() {

        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(BUFFER_SIZE); // Using DirectBuffer for zero-copy
            allocations.incrementAndGet();
        }
        buffer.clear();
        poolKeys.remove(System.identityHashCode(buffer));
        return buffer;
    }

    @Synchronized
    public static int release(@Nullable ByteBuffer buffer) {
        if (buffer == null) {
            Timber.d("Trying to release a null buffer...skip");
            return ENULL;
        }

        int key = System.identityHashCode(buffer);
        if (poolKeys.contains(key)) {
            Timber.d("Trying to release a buffer that is already in the pool...skip");
            return EEXIST;
        }
        buffer.clear();
        pool.offer(buffer);
        poolKeys.add(key);
        atomicUpdateAndGet(allocations, value -> value > 0 ? value - 1 : 0);

        return 0;
    }

    private static void atomicUpdateAndGet(AtomicLong atomicLong, UpdateAllocation updateFunction) {
        long prev, next;
        do {
            prev = atomicLong.get();
            next = updateFunction.applyUpdate(prev);
        } while (!atomicLong.compareAndSet(prev, next));
    }

    private interface UpdateAllocation {
        long applyUpdate(long operand);
    }

    static final int ENULL = 1;
    static final int EEXIST = 2;
}
