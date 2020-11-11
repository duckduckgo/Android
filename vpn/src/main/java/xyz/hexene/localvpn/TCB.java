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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import timber.log.Timber;

/** Transmission Control Block */
public class TCB {
    public String ipAndPort;

    public long sequenceNumberToClient, sequenceNumberToClientInitial;
    public long sequenceNumberToServer, sequenceNumberToServerInitial;
    public long acknowledgementNumberToClient, acknowledgementNumberToServer;
    public long finSequenceNumberToClient = -1;

    public TcbState tcbState;

    public boolean isTracker = false;
    public boolean trackerTypeDetermined = false;

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

    public Packet referencePacket;

    public SocketChannel channel;
    public boolean waitingForNetworkData;
    public SelectionKey selectionKey;

    private static final int MAX_CACHE_SIZE = 50; // XXX: Is this ideal?
    public static LRUCache<String, TCB> tcbCache =
            new LRUCache<>(
                    MAX_CACHE_SIZE,
                    new LRUCache.CleanupCallback<String, TCB>() {
                        @Override
                        public void cleanup(Map.Entry<String, TCB> eldest) {
                            Timber.w("Closing old TCB: %s", eldest.getKey());
                            eldest.getValue().closeChannel();
                        }
                    });

    public static TCB getTCB(String ipAndPort) {
        synchronized (tcbCache) {
            return tcbCache.get(ipAndPort);
        }
    }

    public static void putTCB(String ipAndPort, TCB tcb) {
        synchronized (tcbCache) {
            tcbCache.put(ipAndPort, tcb);
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

        this.sequenceNumberToClient = sequenceNumberToClient;
        this.sequenceNumberToClientInitial = sequenceNumberToClient;
        this.sequenceNumberToServer = sequenceNumberToServer;
        this.sequenceNumberToServerInitial = sequenceNumberToServer;
        this.acknowledgementNumberToClient = acknowledgementNumberToClient;
        this.acknowledgementNumberToServer = acknowledgementNumberToServer;

        this.channel = channel;
        this.referencePacket = referencePacket;
    }

    public static void closeTCB(TCB tcb) {
        tcb.closeChannel();
        synchronized (tcbCache) {
            tcbCache.remove(tcb.ipAndPort);
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
