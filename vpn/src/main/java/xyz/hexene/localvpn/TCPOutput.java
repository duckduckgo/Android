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

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

import timber.log.Timber;
import xyz.hexene.localvpn.Packet.TCPHeader;
import xyz.hexene.localvpn.TCB.TCBStatus;

public class TCPOutput implements Runnable
{
    private static final String TAG = TCPOutput.class.getSimpleName();

    private VpnService vpnService;
    private BlockingQueue<Packet> inputQueue;
    private Queue<ByteBuffer> outputQueue;
    private Selector selector;

    private Random random = new Random();
    public TCPOutput(BlockingQueue<Packet> inputQueue, Queue<ByteBuffer> outputQueue, Selector selector, VpnService vpnService)
    {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.selector = selector;
        this.vpnService = vpnService;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Started");
        try
        {

            Thread currentThread = Thread.currentThread();
            while (true)
            {
                Packet currentPacket = inputQueue.take();

                if (currentThread.isInterrupted())
                    break;

                ByteBuffer payloadBuffer = currentPacket.backingBuffer;
                currentPacket.backingBuffer = null;
                ByteBuffer responseBuffer = ByteBufferPool.acquire();

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;

                TCPHeader tcpHeader = currentPacket.tcpHeader;
                int destinationPort = tcpHeader.destinationPort;
                int sourcePort = tcpHeader.sourcePort;

                String ipAndPort = destinationAddress.getHostAddress() + ":" +
                        destinationPort + ":" + sourcePort;

                Timber.i("Processing TCP device-to-network packet %s", ipAndPort);

                TCB tcb = TCB.getTCB(ipAndPort);
                if (tcb == null)
                    initializeConnection(ipAndPort, destinationAddress, destinationPort,
                            currentPacket, tcpHeader, responseBuffer);
                else if (tcpHeader.isSYN())
                    processDuplicateSYN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isRST())
                    closeCleanly(tcb, responseBuffer);
                else if (tcpHeader.isFIN())
                    processFIN(tcb, tcpHeader, responseBuffer);
                else if (tcpHeader.isACK())
                    processACK(tcb, tcpHeader, payloadBuffer, responseBuffer);

                // XXX: cleanup later
                if (responseBuffer.position() == 0)
                    ByteBufferPool.release(responseBuffer);
                ByteBufferPool.release(payloadBuffer);
            }
        }
        catch (InterruptedException e)
        {
            Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
            Log.e(TAG, e.toString(), e);
        }
        finally
        {
            TCB.closeAll();
        }
    }

    private void initializeConnection(String ipAndPort, InetAddress destinationAddress, int destinationPort,
                                      Packet currentPacket, TCPHeader header, ByteBuffer responseBuffer)
            throws IOException
    {
        currentPacket.swapSourceAndDestination();
        if (header.isSYN())
        {
            SocketChannel outputChannel = SocketChannel.open();
            outputChannel.configureBlocking(false);
            vpnService.protect(outputChannel.socket());

            TCB tcb = new TCB(ipAndPort,
                    random.nextInt(Short.MAX_VALUE + 1),
                    header.sequenceNumber,
                    header.sequenceNumber + 1,
                    header.acknowledgementNumber,
                    outputChannel, currentPacket);
            TCB.putTCB(ipAndPort, tcb);

            try
            {
                Log.d("VPN", destinationAddress.getCanonicalHostName());

                outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                if (outputChannel.finishConnect())
                {
                    tcb.status = TCBStatus.SYN_RECEIVED;
                    // TODO: Set MSS for receiving larger packets from the device
                    currentPacket.updateTCPBuffer(responseBuffer, (byte) (TCPHeader.SYN | TCPHeader.ACK),
                            tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
                    tcb.mySequenceNum++; // SYN counts as a byte
                }
                else
                {
                    tcb.status = TCBStatus.SYN_SENT;
                    selector.wakeup();
                    tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_CONNECT, tcb);
                    return;
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "Connection error: " + ipAndPort, e);
                currentPacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum, 0);
                TCB.closeTCB(tcb);
            }
        }
        else
        {
            currentPacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.RST,
                    0, header.sequenceNumber + 1, 0);
        }
        outputQueue.offer(responseBuffer);
    }

    private void processDuplicateSYN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            if (tcb.status == TCBStatus.SYN_SENT)
            {
                tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
                return;
            }
        }
        Log.w("TCPOutput", "Duplicate SYN... sending RST packet");
        sendRST(tcb, 1, responseBuffer);
    }

    private void processFIN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            Packet referencePacket = tcb.referencePacket;
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + 1;
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;

            if (tcb.waitingForNetworkData)
            {
                tcb.status = TCBStatus.CLOSE_WAIT;
                referencePacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK,
                        tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
            }
            else
            {
                tcb.status = TCBStatus.LAST_ACK;
                referencePacket.updateTCPBuffer(responseBuffer, (byte) (TCPHeader.FIN | TCPHeader.ACK),
                        tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
                tcb.mySequenceNum++; // FIN counts as a byte
            }
        }
        outputQueue.offer(responseBuffer);
    }

    private void processACK(TCB tcb, TCPHeader tcpHeader, ByteBuffer payloadBuffer, ByteBuffer responseBuffer) throws IOException
    {
        int payloadSize = payloadBuffer.limit() - payloadBuffer.position();

        synchronized (tcb)
        {
            SocketChannel outputChannel = tcb.channel;
            if (tcb.status == TCBStatus.SYN_RECEIVED)
            {
                tcb.status = TCBStatus.ESTABLISHED;

                selector.wakeup();
                tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_READ, tcb);
                tcb.waitingForNetworkData = true;
            }
            else if (tcb.status == TCBStatus.LAST_ACK)
            {
                closeCleanly(tcb, responseBuffer);
                return;
            }

            if (payloadSize == 0) return; // Empty ACK, ignore

            if (!tcb.waitingForNetworkData)
            {
                selector.wakeup();
                tcb.selectionKey.interestOps(SelectionKey.OP_READ);
                tcb.waitingForNetworkData = true;
            }

            // Forward to remote server
            try
            {
                while (payloadBuffer.hasRemaining())
                    outputChannel.write(payloadBuffer);
            }
            catch (IOException e)
            {
                Log.e(TAG, "Network write error: " + tcb.ipAndPort, e);
                sendRST(tcb, payloadSize, responseBuffer);
                return;
            }

            // TODO: We don't expect out-of-order packets, but verify
            tcb.myAcknowledgementNum = tcpHeader.sequenceNumber + payloadSize;
            tcb.theirAcknowledgementNum = tcpHeader.acknowledgementNumber;
            Packet referencePacket = tcb.referencePacket;
            referencePacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb.mySequenceNum, tcb.myAcknowledgementNum, 0);
        }
        outputQueue.offer(responseBuffer);
    }

    private void sendRST(TCB tcb, int prevPayloadSize, ByteBuffer responseBuffer)
    {
        tcb.referencePacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum + prevPayloadSize, 0);
        outputQueue.offer(responseBuffer);
        TCB.closeTCB(tcb);
    }

    private void closeCleanly(TCB tcb, ByteBuffer buffer)
    {
        ByteBufferPool.release(buffer);
        TCB.closeTCB(tcb);
    }
}
