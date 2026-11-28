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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat

/**
 * A VPN lifecycle request the service runs through [VpnOperationQueue].
 * startId is the service start id of the onStartCommand that produced the request; stopVpn passes it to stopSelfResult.
 */
internal sealed interface VpnOperation {
    /** Brings the VPN up with the latest state: a fresh start or a restart, decided by startVpn itself. */
    data class Start(
        val alwaysOnTriggered: Boolean,
        val waitForForeground: Boolean,
        val startId: Int,
        /** How many later Start requests were merged into this one while it was pending. */
        val coalescedRequests: Int = 0,
    ) : VpnOperation

    data class Stop(val reason: VpnStopReason, val startId: Int) : VpnOperation

    /** Service is being destroyed: tear down the network stack. Terminal. */
    data object Destroy : VpnOperation
}

/**
 * Runs VPN lifecycle operations one at a time, each to completion, in submission order.
 *
 * A start suspends at several points, so running every request in its own coroutine on a single-threaded dispatcher
 * still interleaves them, and one restart could destroy the network stack another had just started. Pending
 * operations (submitted, not yet started) are collapsed:
 * - consecutive starts merge into one;
 * - a stop drops every pending start and replaces a pending stop, unless that one is a revocation, whose reason is kept;
 * - a destroy drops pending starts, keeps a pending stop to run before it, and closes the queue.
 * Merged operations keep the latest start id. The running operation is never cancelled.
 */
internal class VpnOperationQueue(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val execute: suspend (VpnOperation) -> Unit,
) {
    private val lock = Any()
    private val pending = ArrayDeque<VpnOperation>()
    private var destroyed = false
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(dispatcher) {
            while (true) {
                val operation = synchronized(lock) { pending.removeFirstOrNull() }
                if (operation == null) {
                    // submit() signals after it enqueues, and the conflated channel keeps that signal until it's received
                    wakeUp.receive()
                    continue
                }
                logcat { "VPN log: running $operation" }
                execute(operation)
                if (operation == VpnOperation.Destroy) break
            }
        }
    }

    /** Queues [operation]. Safe to call from any thread, including the main thread: it never suspends. */
    fun submit(operation: VpnOperation) {
        val accepted = synchronized(lock) {
            if (destroyed) {
                false
            } else {
                enqueue(operation)
                true
            }
        }
        if (accepted) {
            wakeUp.trySend(Unit)
        } else {
            logcat(WARN) { "VPN log: ignoring $operation, the service is being destroyed" }
        }
    }

    private fun enqueue(operation: VpnOperation) {
        when (operation) {
            is VpnOperation.Start -> {
                val last = pending.lastOrNull()
                if (last is VpnOperation.Start) {
                    pending[pending.lastIndex] = VpnOperation.Start(
                        alwaysOnTriggered = last.alwaysOnTriggered || operation.alwaysOnTriggered,
                        waitForForeground = last.waitForForeground || operation.waitForForeground,
                        startId = maxOf(last.startId, operation.startId),
                        coalescedRequests = last.coalescedRequests + 1 + operation.coalescedRequests,
                    )
                } else {
                    pending.addLast(operation)
                }
            }
            is VpnOperation.Stop -> {
                pending.removeAll { it is VpnOperation.Start }
                // with the starts gone, a pending stop can only be the last operation
                val last = pending.lastOrNull()
                if (last is VpnOperation.Stop) {
                    pending[pending.lastIndex] = VpnOperation.Stop(
                        reason = if (last.reason == VpnStopReason.REVOKED) last.reason else operation.reason,
                        startId = maxOf(last.startId, operation.startId),
                    )
                } else {
                    pending.addLast(operation)
                }
            }
            VpnOperation.Destroy -> {
                pending.removeAll { it is VpnOperation.Start }
                pending.addLast(operation)
                destroyed = true
            }
        }
    }
}
