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

package com.duckduckgo.sync.internal.ui

import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.NotSentReason
import com.duckduckgo.sync.impl.exchange.v2.PeerVersionSource
import com.duckduckgo.sync.impl.exchange.v2.RejectReason
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogRow(
    val id: Long,
    val timestampText: String,
    val eventType: Type,
    val summary: String,
    val details: String?,
    val rawJson: String?,
    val prettyJson: String?,
) {
    val isExpandable = details != null || prettyJson != null

    // Order here determines how the categories are displayed in the filter dialog
    enum class Category(val label: String) {
        Session("Session events"),
        Message("Message events"),
        Dev("Dev events"),
    }

    enum class Type(val label: String, val category: Category) {
        SessionStarted("Session started", Category.Session),
        VersionNegotiated("Version negotiated", Category.Session),
        RoleElected("Role elected", Category.Session),
        StateTransition("State transition", Category.Session),
        MessageSent("Message sent", Category.Message),
        MessageNotSent("Message not sent", Category.Message),
        MessageReceived("Message received", Category.Message),
        MessageRejected("Message rejected", Category.Message),
        SessionError("Session error", Category.Session),
        SessionEnded("Session ended", Category.Session),
        DevTool("Dev tool", Category.Dev),
        ;

        companion object {
            fun fromEvent(event: ExchangeV2Event): Type = when (event) {
                is ExchangeV2Event.SessionStarted -> SessionStarted
                is ExchangeV2Event.VersionNegotiated -> VersionNegotiated
                is ExchangeV2Event.RoleElected -> RoleElected
                is ExchangeV2Event.Transition -> StateTransition
                is ExchangeV2Event.MessageSent -> MessageSent
                is ExchangeV2Event.MessageNotSent -> MessageNotSent
                is ExchangeV2Event.MessageReceived -> MessageReceived
                is ExchangeV2Event.MessageRejected -> MessageRejected
                is ExchangeV2Event.SessionError -> SessionError
                is ExchangeV2Event.SessionEnded -> SessionEnded
            }
        }
    }

    companion object {
        fun from(event: ExchangeV2Event, id: Long): LogRow {
            val rawJson = event.debugRawJson()
            return LogRow(
                id = id,
                timestampText = event.timestampMs.toLogTimestamp(),
                eventType = Type.fromEvent(event),
                summary = event.debugSummary(),
                details = event.debugDetails(),
                rawJson = rawJson,
                prettyJson = rawJson?.prettyPrintJson(),
            )
        }

        fun devTool(id: Long, message: String, timestampMs: Long = System.currentTimeMillis()) = LogRow(
            id = id,
            timestampText = timestampMs.toLogTimestamp(),
            eventType = Type.DevTool,
            summary = message,
            details = null,
            rawJson = null,
            prettyJson = null,
        )
    }
}

private val logTimestampFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

private fun Long.toLogTimestamp(): String = logTimestampFormat.get()!!.format(Date(this))

private fun ExchangeV2Event.debugSummary(): String = when (this) {
    is ExchangeV2Event.SessionStarted -> "Started as $pairingRole"
    is ExchangeV2Event.VersionNegotiated -> "Negotiated ${negotiatedVersion.prettyPrint()}"
    is ExchangeV2Event.RoleElected -> "Elected $role"
    is ExchangeV2Event.Transition -> "${from.debugLabel()} → ${to.debugLabel()}"
    is ExchangeV2Event.MessageSent -> message.messageType
    is ExchangeV2Event.MessageNotSent -> "$messageType: ${reason.debugLabel()}"
    is ExchangeV2Event.MessageReceived -> message.messageType
    is ExchangeV2Event.MessageRejected -> "${message.messageType}: ${reason.debugLabel()}"
    is ExchangeV2Event.SessionError -> kind.name
    is ExchangeV2Event.SessionEnded -> "Ended (${byeReason.value})"
}

private fun ExchangeV2Event.debugDetails(): String {
    val lines = mutableListOf<String>()
    when (this) {
        is ExchangeV2Event.SessionStarted -> {
            lines += "Role: $pairingRole"
            lines += "Own channel: $ownChannelId"
            lines += linkingCode?.let { "Linking code: $it" } ?: "No linking code (Scanner side)"
        }
        is ExchangeV2Event.VersionNegotiated -> {
            lines += "Our version: ${ourVersion.prettyPrint()}"
            lines += "Peer version: ${peerVersion.prettyPrint()} (from ${peerSource.debugLabel()})"
            lines += "Negotiated: ${negotiatedVersion.prettyPrint()}"
        }
        is ExchangeV2Event.RoleElected -> {
            lines += "Elected: $role"
            lines += "Own: role=$ownPairingRole, kind=$ownKind, signedIn=$ownSignedIn"
            lines += "Peer: kind=$peerKind, signedIn=$peerSignedIn"
        }
        is ExchangeV2Event.Transition -> {
            lines += "From: ${from.debugLabel()}"
            lines += "To: ${to.debugLabel()}"
            val trigger = trigger
            val localTrigger = localTrigger
            when {
                trigger != null -> {
                    lines += "Trigger: message ${trigger.messageType}"
                    lines += "Requires protocol: ${trigger.protocolVersion.prettyPrint()}"
                }
                localTrigger != null -> lines += "Trigger: local ${localTrigger.debugLabel()}"
                else -> lines += "Trigger: (none)"
            }
        }
        is ExchangeV2Event.MessageSent -> {
            lines += "Requires protocol: ${message.protocolVersion.prettyPrint()}"
        }
        is ExchangeV2Event.MessageNotSent -> {
            lines += "Message: $messageType"
            lines += "Failure reason: ${reason.debugLabel()}"
            message?.let { lines += "Requires protocol: ${it.protocolVersion.prettyPrint()}" }
        }
        is ExchangeV2Event.MessageReceived -> {
            lines += "Message: ${message.messageType}"
            lines += "Requires protocol: ${message.protocolVersion.prettyPrint()}"
        }
        is ExchangeV2Event.MessageRejected -> {
            lines += "Message: ${message.messageType}"
            lines += "State: ${state.debugLabel()}"
            lines += "Failure reason: ${reason.debugLabel()}"
            lines += "Requires protocol: ${message.protocolVersion.prettyPrint()}"
        }
        is ExchangeV2Event.SessionError -> {
            lines += "Kind: ${kind.name}"
            lines += "Message: $message"
            timeoutStage?.let { lines += "Timeout stage: ${it.value}" }
        }
        is ExchangeV2Event.SessionEnded -> {
            lines += "Last state: ${lastState.debugLabel()}"
            lines += "Bye reason: ${byeReason.value}"
        }
    }
    return lines.joinToString("\n")
}

private fun ExchangeV2Event.debugRawJson(): String? = when (this) {
    is ExchangeV2Event.Transition -> trigger?.rawJson
    is ExchangeV2Event.MessageSent -> message.rawJson
    is ExchangeV2Event.MessageNotSent -> message?.rawJson
    is ExchangeV2Event.MessageReceived -> message.rawJson
    is ExchangeV2Event.MessageRejected -> message.rawJson
    is ExchangeV2Event.SessionStarted,
    is ExchangeV2Event.VersionNegotiated,
    is ExchangeV2Event.RoleElected,
    is ExchangeV2Event.SessionError,
    is ExchangeV2Event.SessionEnded,
    -> null
}

private fun String.prettyPrintJson(): String = runCatching { JSONObject(this).toString(2) }.getOrDefault(this)

internal fun ExchangeV2State?.debugLabel() = when (this) {
    null -> "(no session)"
    ExchangeV2State.Bootstrapped -> "Bootstrapped"
    ExchangeV2State.Negotiating -> "Negotiating"
    ExchangeV2State.SameAccountAbort -> "SameAccountAbort"
    ExchangeV2State.Aborted -> "Aborted"
    ExchangeV2State.Host.Confirming -> "Host.Confirming"
    ExchangeV2State.Host.Sending -> "Host.Sending"
    ExchangeV2State.Host.AwaitingStatus -> "Host.AwaitingStatus"
    ExchangeV2State.Host.Unknown -> "Host.Unknown"
    ExchangeV2State.Host.Aborted -> "Host.Aborted"
    ExchangeV2State.Host.Done -> "Host.Done"
    ExchangeV2State.Joiner.Confirming -> "Joiner.Confirming"
    ExchangeV2State.Joiner.Waiting -> "Joiner.Waiting"
    ExchangeV2State.Joiner.Joining -> "Joiner.Joining"
    ExchangeV2State.Joiner.AbortedLocal -> "Joiner.AbortedLocal"
    ExchangeV2State.Joiner.AbortedByHost -> "Joiner.AbortedByHost"
    ExchangeV2State.Joiner.Done -> "Joiner.Done"
    ExchangeV2State.Joiner.JoinFailed -> "Joiner.JoinFailed"
}

private fun LocalTrigger.debugLabel(): String = when (this) {
    LocalTrigger.UserConfirmedHost -> "UserConfirmedHost"
    LocalTrigger.UserDeniedHost -> "UserDeniedHost"
    LocalTrigger.UserConfirmedJoiner -> "UserConfirmedJoiner"
    LocalTrigger.UserDeniedJoiner -> "UserDeniedJoiner"
    is LocalTrigger.HostSendComplete -> "HostSendComplete(${negotiatedVersion.prettyPrint()})"
    is LocalTrigger.JoinerJoinComplete -> "JoinerJoinComplete(${reason.value})"
    LocalTrigger.HostUnavailable -> "HostUnavailable"
    is LocalTrigger.RoleElected -> "RoleElected($role)"
    is LocalTrigger.HostStatusDeadlineElapsed -> "HostStatusDeadlineElapsed($deadline)"
}

private fun NotSentReason.debugLabel(): String = when (this) {
    NotSentReason.OwnChannelNotConfigured -> "OwnChannelNotConfigured"
    is NotSentReason.HttpError -> "HttpError($code)"
    is NotSentReason.TooHighProtocol -> "TooHighProtocol(negotiated ${negotiatedVersion.prettyPrint()})"
}

private fun RejectReason.debugLabel(): String = when (this) {
    RejectReason.ImplicitAbort -> "ImplicitAbort"
    RejectReason.SameAccount -> "SameAccount"
    RejectReason.UnknownMessageDropped -> "UnknownMessageDropped"
    RejectReason.TooHighProtocolDropped -> "TooHighProtocolDropped"
    RejectReason.PeerLeft -> "PeerLeft"
}

private fun PeerVersionSource.debugLabel(): String = when (this) {
    PeerVersionSource.LinkingCode -> "linking_code"
    PeerVersionSource.HelloMessage -> "hello_message"
}
