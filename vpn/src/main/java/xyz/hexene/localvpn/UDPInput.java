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

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

public class UDPInput implements Runnable
{
    private static final String TAG = UDPInput.class.getSimpleName();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;

    private Selector selector;
    private Queue<ByteBuffer> outputQueue;

    public UDPInput(Queue<ByteBuffer> outputQueue, Selector selector)
    {
        this.outputQueue = outputQueue;
        this.selector = selector;
    }

    @Override
    public void run()
    {
        try
        {
            Log.i(TAG, "Started");
            while (!Thread.interrupted())
            {
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(10);
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted())
                {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid() && key.isReadable())
                    {
                        keyIterator.remove();

                        ByteBuffer receiveBuffer = ByteBufferPool.acquire();
                        // Leave space for the header
                        receiveBuffer.position(HEADER_SIZE);

                        DatagramChannel inputChannel = (DatagramChannel) key.channel();
                        // XXX: We should handle any IOExceptions here immediately,
                        // but that probably won't happen with UDP
                        int readBytes = inputChannel.read(receiveBuffer);

                        Packet referencePacket = (Packet) key.attachment();
                        referencePacket.updateUDPBuffer(receiveBuffer, readBytes);
                        receiveBuffer.position(HEADER_SIZE + readBytes);

                        outputQueue.offer(receiveBuffer);
                    }
                }
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.w(TAG, e.toString(), e);
        }
    }
}
