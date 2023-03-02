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

package main

/*
Implementation of the TUN device interface for Android (wraps linux one)
 */


// #include <android/log.h>
// extern int is_pkt_allowed(char *buffer, int length);
// extern int wg_write_pcap(char *buffer, int length);
import "C"

import (
    "os"
    "unsafe"

    "golang.org/x/net/ipv4"
    "golang.zx2c4.com/wireguard/tun"
)

type NativeTunWrapper struct {
    nativeTun tun.Device
}

func (tunWrapper *NativeTunWrapper) File() *os.File {
    return tunWrapper.nativeTun.File()
}

func (tunWrapper *NativeTunWrapper) MTU() (int, error) {
    return tunWrapper.nativeTun.MTU()
}

func (tunWrapper *NativeTunWrapper) Name() (string, error) {
    return tunWrapper.nativeTun.Name()
}

func (tunWrapper *NativeTunWrapper) Write(buf []byte, offset int) (int, error) {
    pktLen, err :=  tunWrapper.nativeTun.Write(buf, offset)

    tag := cstring("WireGuard/GoBackend/Write")

    // PCAP recording
    pcap_res := int(C.wg_write_pcap((*C.char)(unsafe.Pointer(&buf[offset])), C.int(pktLen+offset)))
    if pcap_res < 0 {
        C.__android_log_write(C.ANDROID_LOG_DEBUG, tag, cstring("PCAP packet not written"))
    }

    return pktLen, err
}

func (tunWrapper *NativeTunWrapper) Flush() error {
    return nil
}

func (tunWrapper *NativeTunWrapper) Read(buf []byte, offset int) (int, error) {
    pktLen, err := tunWrapper.nativeTun.Read(buf, offset)

    tag := cstring("WireGuard/GoBackend/Read")
    switch buf[offset] >> 4 {
        case ipv4.Version:
            if len(buf) < ipv4.HeaderLen {
                C.__android_log_write(C.ANDROID_LOG_DEBUG, tag, cstring("Skipping bad IPv4 pkt"))
                return pktLen, err
            }

            // Check if TCP
            protocol := buf[offset + 9]
            if protocol != 0x06 {
                // Skip checking with AppTP since for now we only check TCP connections
                return pktLen, err
            }

            // PCAP recording
            pcap_res := int(C.wg_write_pcap((*C.char)(unsafe.Pointer(&buf[offset])), C.int(pktLen+offset)))
            if pcap_res < 0 {
                C.__android_log_write(C.ANDROID_LOG_DEBUG, tag, cstring("PCAP packet not written"))
            }

            allow := int(C.is_pkt_allowed((*C.char)(unsafe.Pointer(&buf[offset])), C.int(pktLen+offset)))
            if allow == 0 {
                // Returning 0 blocks the connection since we will not forward this packet
                C.__android_log_write(C.ANDROID_LOG_DEBUG, tag, cstring("Blocking connection"))
                return 0, err
            }

        // TODO: IPv6
        default:
            C.__android_log_write(C.ANDROID_LOG_DEBUG, tag, cstring("Invalid IP"))
    }

    return pktLen, err
}

func (tunWrapper *NativeTunWrapper) Events() chan tun.Event {
    return tunWrapper.nativeTun.Events()
}

func (tunWrapper *NativeTunWrapper) Close() error {
    return tunWrapper.nativeTun.Close()
}

func CreateAndroidTUNFromFD(fd int) (tun.Device, string, error) {
    nativeTun, name, err := tun.CreateUnmonitoredTUNFromFD(fd)
    if err != nil {
        return nil, name, err
    }

    device := &NativeTunWrapper{
        nativeTun: nativeTun,
    }

    return device, name, err
}
