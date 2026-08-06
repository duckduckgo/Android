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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.ui.nativeinput.edit.AdoptedFile
import com.duckduckgo.duckchat.impl.ui.nativeinput.edit.AdoptedImage
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class EditPromptPayload(
    val prompt: String,
    val images: List<AdoptedImage>,
    val files: List<AdoptedFile>,
)

sealed interface EditPromptResult {
    data class Submitted(
        val prompt: String,
        val images: List<AdoptedImage>,
        val files: List<AdoptedFile>,
    ) : EditPromptResult

    data object Cancelled : EditPromptResult
}

/**
 * Hands an edit payload to the edit screen and carries its outcome back to the suspended JS handler.
 * Attachments cross as base64 and can be megabytes, so they cannot travel in the launch Intent.
 */
interface EditPromptSessionStore {
    fun open(payload: EditPromptPayload): String
    fun payload(sessionId: String): EditPromptPayload?
    suspend fun await(sessionId: String): EditPromptResult
    fun resolve(sessionId: String, result: EditPromptResult)
    fun clear(sessionId: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealEditPromptSessionStore @Inject constructor() : EditPromptSessionStore {

    private class Session(
        val payload: EditPromptPayload,
        val result: CompletableDeferred<EditPromptResult> = CompletableDeferred(),
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    override fun open(payload: EditPromptPayload): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = Session(payload)
        return sessionId
    }

    override fun payload(sessionId: String): EditPromptPayload? = sessions[sessionId]?.payload

    override suspend fun await(sessionId: String): EditPromptResult {
        val session = sessions[sessionId] ?: return EditPromptResult.Cancelled
        return try {
            session.result.await()
        } finally {
            sessions.remove(sessionId)
        }
    }

    override fun resolve(
        sessionId: String,
        result: EditPromptResult,
    ) {
        // complete() is a no-op once completed, so the first outcome wins and a late teardown
        // cannot overwrite a submission.
        sessions[sessionId]?.result?.complete(result)
    }

    override fun clear(sessionId: String) {
        sessions.remove(sessionId)?.result?.complete(EditPromptResult.Cancelled)
    }
}
