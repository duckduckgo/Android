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

package com.duckduckgo.sync.impl

import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncService.Companion.SYNC_PROD_ENVIRONMENT_URL
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.store.SyncStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

class SyncServiceRemoteAuditLogTest {

    private val syncService: SyncService = mock()
    private val syncStore: SyncStore = mock()
    private val call: Call<AuditLogResponse> = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
        preventStaleTokenLogout().setRawStoredState(State(true))
    }
    private val syncRemote = SyncServiceRemote(syncService, syncStore, mock(), syncFeature)

    @Before
    fun setup() {
        whenever(syncStore.token).thenReturn(TOKEN)
        whenever(syncService.getAuditLog(any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(call)
    }

    @Test
    fun whenGetLatestThenNoCursorSentAndLimitPassedThrough() {
        whenever(call.execute()).thenReturn(Response.success(AuditLogResponse(PAGE)))

        syncRemote.getAuditLogLatest(TOKEN, limit = 1)

        verify(syncService).getAuditLog("Bearer $TOKEN", after = null, before = null, limit = 1)
    }

    @Test
    fun whenGetLatestWithoutLimitThenNoLimitSent() {
        whenever(call.execute()).thenReturn(Response.success(AuditLogResponse(PAGE)))

        syncRemote.getAuditLogLatest(TOKEN)

        verify(syncService).getAuditLog("Bearer $TOKEN", after = null, before = null, limit = null)
    }

    @Test
    fun whenGetAfterThenOnlyAfterCursorSent() {
        whenever(call.execute()).thenReturn(Response.success(AuditLogResponse(PAGE)))

        syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        verify(syncService).getAuditLog("Bearer $TOKEN", after = "after-cursor", before = null, limit = null)
    }

    @Test
    fun whenGetBeforeThenOnlyBeforeCursorSent() {
        whenever(call.execute()).thenReturn(Response.success(AuditLogResponse(PAGE)))

        syncRemote.getAuditLogBefore(TOKEN, cursor = "before-cursor")

        verify(syncService).getAuditLog("Bearer $TOKEN", after = null, before = "before-cursor", limit = null)
    }

    @Test
    fun whenRequestSucceedsThenReturnPage() {
        whenever(call.execute()).thenReturn(Response.success(AuditLogResponse(PAGE)))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.Success(PAGE), result)
    }

    @Test
    fun whenSuccessHasNoBodyThenReturnServerError() {
        whenever(call.execute()).thenReturn(Response.success(null))

        val result = syncRemote.getAuditLogLatest(TOKEN)

        assertEquals(AuditLogResult.ServerError(statusCode = 200), result)
    }

    @Test
    fun whenStatus418WithEmptyBodyThenReturnRateLimited() {
        whenever(call.execute()).thenReturn(errorResponse(418, body = ""))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.RateLimited(statusCode = 418), result)
    }

    @Test
    fun whenStatus429ThenReturnRateLimited() {
        whenever(call.execute()).thenReturn(errorResponse(429))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.RateLimited(statusCode = 429), result)
    }

    @Test
    fun whenStatus400InvalidCursorThenReturnInvalidCursor() {
        whenever(call.execute()).thenReturn(errorResponse(400, body = """{"code":400,"error":"invalid_cursor"}"""))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.InvalidCursor, result)
    }

    @Test
    fun whenStatus400BadRequestThenReturnInvalidCursor() {
        whenever(call.execute()).thenReturn(errorResponse(400, body = """{"code":400,"error":"bad_request"}"""))

        val result = syncRemote.getAuditLogBefore(TOKEN, cursor = "before-cursor")

        assertEquals(AuditLogResult.InvalidCursor, result)
    }

    @Test
    fun whenStatus401ThenReturnAuthErrorAndClearStore() {
        whenever(call.execute()).thenReturn(errorResponse(401))

        val result = syncRemote.getAuditLogLatest(TOKEN)

        assertEquals(AuditLogResult.AuthError(statusCode = 401), result)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenStatus401ForRotatedTokenThenReturnAuthErrorAndDoNotClearStore() {
        whenever(syncStore.token).thenReturn("rotated-token")
        whenever(call.execute()).thenReturn(errorResponse(401))

        val result = syncRemote.getAuditLogLatest(TOKEN)

        assertEquals(AuditLogResult.AuthError(statusCode = 401), result)
        verify(syncStore, never()).clearAll()
    }

    @Test
    fun whenStatus403ThenReturnAuthErrorAndDoNotClearStore() {
        whenever(call.execute()).thenReturn(errorResponse(403))

        val result = syncRemote.getAuditLogLatest(TOKEN)

        assertEquals(AuditLogResult.AuthError(statusCode = 403), result)
        verify(syncStore, never()).clearAll()
    }

    @Test
    fun whenStatus5xxThenReturnServerError() {
        whenever(call.execute()).thenReturn(errorResponse(503))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.ServerError(statusCode = 503), result)
    }

    @Test
    fun whenRequestThrowsIOExceptionThenReturnNetworkError() {
        whenever(call.execute()).thenThrow(IOException("no network"))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.NetworkError, result)
    }

    @Test
    fun whenRequestThrowsOtherExceptionThenReturnServerErrorWithoutStatus() {
        whenever(call.execute()).thenThrow(IllegalStateException("bad body"))

        val result = syncRemote.getAuditLogAfter(TOKEN, cursor = "after-cursor")

        assertEquals(AuditLogResult.ServerError(statusCode = null), result)
    }

    private fun errorResponse(
        code: Int,
        body: String = """{"code":$code,"error":"error"}""",
    ): Response<AuditLogResponse> {
        val request = Request.Builder()
            .url("$SYNC_PROD_ENVIRONMENT_URL/sync/account/audit-log")
            .header("Authorization", "Bearer $TOKEN")
            .build()
        val raw = okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .build()
        return Response.error(body.toResponseBody("application/json".toMediaTypeOrNull()), raw)
    }

    private companion object {
        const val TOKEN = "token"
        val PAGE = AuditLogPage(
            entries = listOf(AuditLogEntry(id = "entry-1", rawVersion = "1", payload = "jwe")),
            after = "next-after",
            before = "next-before",
        )
    }
}
