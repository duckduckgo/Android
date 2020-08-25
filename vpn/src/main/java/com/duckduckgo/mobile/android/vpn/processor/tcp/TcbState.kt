/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import xyz.hexene.localvpn.TCB

data class TcbState(
    val clientState: TCB.TCBStatus = TCB.TCBStatus.CLOSED,
    val serverState: TCB.TCBStatus = TCB.TCBStatus.LISTEN
) {
    override fun toString(): String {
        return "Client: $clientState. Server: $serverState"
    }
}

class TCPStateReducer(){

//    fun reduce(currentState: TcbState, action: TcpStateFlow.TcpStateAction): TcbState {
//        return when (action.events){
//            is TcpStateFlow.Event.OpenConnection -> openConnection(currentState, action.events)
//            else -> TcbState()
//        }
//    }
//
//    private fun openConnection(currentState: TcbState, event: TcpStateFlow.Event): TcbState {
//        if (currentState.clientState == TCB.TCBStatus.SYN_SENT){
//            // duplicate syn flag sent through, we don't want to do anything
//            return TcpStateFlow.TcpStateAction()
//        }
//        return TcbState(clientState = TCB.TCBStatus.SYN_SENT, serverState = TCB.TCBStatus.LISTEN)
//    }

}