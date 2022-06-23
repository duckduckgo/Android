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
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcbState;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import timber.log.Timber;

public class TCB {
    public final long creationTime;
    public final String ipAndPort;

    public final AtomicLong sequenceNumberToClient = new AtomicLong();
    public final AtomicLong sequenceNumberToClientInitial = new AtomicLong();
    public final long sequenceNumberToServer, sequenceNumberToServerInitial;
    public final AtomicLong acknowledgementNumberToClient = new AtomicLong();
    public final AtomicLong acknowledgementNumberToServer = new AtomicLong();
    public long finSequenceNumberToClient = -1;

    public TcbState tcbState;

    public boolean isTracker = false;
    public boolean trackerTypeDetermined = false;
    public String trackerHostName = null;
    @Nullable public Long stopRespondingTime;

    public Boolean requestingAppDetermined = false;
    public String requestingAppPackage = null;
    public String requestingAppName = null;

    public Boolean connectionEvicted = false;

    @Nullable public String hostName = null;

    // TCP has more states, but we need only these
    public enum TCBStatus {
        LISTEN,
        CLOSED,
        CLOSING,
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        LAST_ACK,
        FIN_WAIT_1,
        FIN_WAIT_2,
        TIME_WAIT
    }

    public final Packet referencePacket;

    public final SocketChannel channel;
    public boolean waitingForNetworkData;

    private static final int MAX_CACHE_SIZE = 500; // XXX: Is this ideal?
    private static final LRUCache<String, TCB> tcbCache =
            new LRUCache<>(
                    MAX_CACHE_SIZE,
                    (LRUCache.CleanupCallback<String, TCB>)
                            eldest -> {
                                TCB evicted = eldest.getValue();
                                evicted.connectionEvicted = true;
                                Timber.w("Closing old TCB: %s", eldest.getKey());
                                evicted.closeChannel();
                            });

    @Nullable
    public static TCB getTCB(String ipAndPort) {
        synchronized (tcbCache) {
            return tcbCache.get(ipAndPort);
        }
    }

    public static void putTCB(String ipAndPort, TCB tcb) {
        synchronized (tcbCache) {
            tcbCache.put(ipAndPort, tcb);
        }
        Timber.v("TCB cache size has now reached %d entries", tcbCache.size());
    }

    public static int size() {
        synchronized (tcbCache) {
            return tcbCache.size();
        }
    }

    public TCB(
            String ipAndPort,
            long sequenceNumberToClient,
            long sequenceNumberToServer,
            long acknowledgementNumberToClient,
            long acknowledgementNumberToServer,
            SocketChannel channel,
            Packet referencePacket) {

        this.tcbState = new TcbState();
        this.ipAndPort = ipAndPort;

        this.sequenceNumberToClient.set(sequenceNumberToClient);
        this.sequenceNumberToClientInitial.set(sequenceNumberToClient);
        this.sequenceNumberToServer = sequenceNumberToServer;
        this.sequenceNumberToServerInitial = sequenceNumberToServer;
        this.acknowledgementNumberToClient.set(acknowledgementNumberToClient);
        this.acknowledgementNumberToServer.set(acknowledgementNumberToServer);

        this.channel = channel;
        this.referencePacket = referencePacket;
        this.creationTime = System.nanoTime();
    }

    public void close() {
        this.closeChannel();
        synchronized (tcbCache) {
            tcbCache.remove(this.ipAndPort);
            Timber.v(
                    "Closed %s. There are now %d connections in the TCB cache",
                    this.ipAndPort, tcbCache.size());
        }
    }

    public static void closeAll() {
        synchronized (tcbCache) {
            Iterator<Map.Entry<String, TCB>> it = tcbCache.entrySet().iterator();
            while (it.hasNext()) {
                it.next().getValue().closeChannel();
                it.remove();
            }
        }
    }

    private void closeChannel() {
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
