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

import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.AuditLogChecker
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import com.duckduckgo.sync.store.SyncStore
import logcat.logcat
import javax.inject.Inject

class AuditLogCheckDebugControls @Inject constructor(
    private val checker: AuditLogChecker,
    private val stateStore: AuditLogCheckStateStore,
    private val syncStore: SyncStore,
) {

    suspend fun unseenCount(): String = "Unseen logins: ${stateStore.read().pendingLogins.size}"

    suspend fun findUnseen(): String {
        val seenBefore = stateStore.read().pendingLogins.map { it.entryId }.toSet()
        val result = checker.check()
        val unseen = stateStore.read().pendingLogins
        return log("${describe(result)}\n\n${describeUnseen(unseen, seenBefore)}")
    }

    suspend fun markAllAsSeen(): String {
        // The state store skips writes while signed out, so without this the tool would report a change that never happened.
        if (!syncStore.isSignedIn()) return "Not signed in: nothing to change"
        val count = stateStore.read().pendingLogins.size
        stateStore.update { it.copy(pendingLogins = emptyList()) }
        return log("Marked $count as seen.\n\n${unseenCount()}")
    }

    private fun describe(result: AuditLogCheckResult): String = when (result) {
        is AuditLogCheckResult.Checked -> buildString {
            append("Found ${result.newLogins} new login${if (result.newLogins == 1) "" else "s"} from other devices.")
            if (result.renamedLogins > 0) append(" Updated the name of ${result.renamedLogins}.")
            if (result.logouts > 0) {
                append(
                    " Also saw ${result.logouts} logout${if (result.logouts == 1) "" else "s"}: tap Read server log to see them.",
                )
            }
        }
        AuditLogCheckResult.Initialised -> "First check for this account: tracking starts now. Older entries are not counted."
        AuditLogCheckResult.Disabled -> "Checks are off. Turn on both flags: loginAuditLog and loginNotification."
        AuditLogCheckResult.NotSignedIn -> "Not signed in."
        AuditLogCheckResult.AlreadyRunning -> "A check is already running (for example after a sync). Try again in a moment."
        AuditLogCheckResult.Discarded -> "Nothing saved: the account or the stored state changed during the check."
        is AuditLogCheckResult.Failed -> "Check failed: ${result.reason}. The next check tries again."
    }

    private fun describeUnseen(unseen: List<PendingLogin>, seenBefore: Set<String>): String = buildString {
        append("Unseen logins: ${unseen.size}")
        unseen.sortedByDescending { it.occurredAt }.forEach {
            val marker = if (it.entryId in seenBefore) "" else "  NEW"
            append("\n\n${it.occurredAt}$marker\n  name: ${it.deviceName}\n  entry: ${it.entryId}\n  device: ${it.deviceId}")
        }
    }

    private fun log(text: String): String {
        logcat { "Sync-AuditLog: internal unseen logins tool:\n$text" }
        return text
    }
}
