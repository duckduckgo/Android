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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import java.util.Base64
import javax.inject.Inject

/**
 * Indirection over [SyncAccountRepository] so [ExchangeV2Runner] can fetch the right recovery
 * code for the Host's `recovery_code_response` based on peer kind, without taking a direct
 * dependency on the much wider repository surface.
 *
 * Returns the b64-encoded `rawCode` form (what the spec wants on the wire), not the QR form.
 * Both raw and QR are currently the same string today, but keeping the distinction here means
 * a future spec tweak only needs editing one line.
 */
interface RecoveryCodeProvider {

    /** Own DDG recovery code — used when peer is a `ddg` device joining our account. */
    fun getDdgRecoveryCode(): Result<String>

    /**
     * Own 3party access credential's recovery code — used when peer is a `3party` device
     * (e.g. Duck.ai web). May fail with a generic error if the 3party credential hasn't been
     * created yet on the account; the runner reports `recovery_code_unavailable` to the peer.
     */
    fun getThirdPartyRecoveryCode(): Result<String>

    /**
     * Spec: Unified Algorithm §"Exchange Share Recovery Code" — *"If host has no account yet,
     * create it first."* Called by the runner at Host.Sending time when no ddg account exists
     * locally. Returns [Result.Success] if the device is now signed in (whether already was, or
     * the just-created), [Result.Error] if account creation failed.
     */
    fun createDdgAccountIfNeeded(): Result<Unit>

    /**
     * Spec: Unified Algorithm §"Exchange Share Recovery Code" — *"If this is ddg and peer is
     * 3party, if needed, extend the account."* Ensures the device has a 3party credential
     * locally, creating one on the BE if absent. Called by the runner before fetching a 3party
     * recovery code to share with a 3party peer. No-op if a 3party credential already exists
     * locally ([SyncStore.scopedPassword] is set). Failure surfaces as [Result.Error] and the
     * runner falls through to `recovery_code_unavailable`.
     */
    fun createThirdPartyCredentialIfNeeded(): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealRecoveryCodeProvider @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStore: com.duckduckgo.sync.store.SyncStore,
) : RecoveryCodeProvider {

    override fun createDdgAccountIfNeeded(): Result<Unit> {
        if (syncStore.userId != null) return Result.Success(Unit)
        return when (val r = syncAccountRepository.createAccount()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> r
        }
    }

    override fun createThirdPartyCredentialIfNeeded(): Result<Unit> {
        if (syncStore.scopedPassword != null) return Result.Success(Unit)
        return when (val r = syncAccountRepository.createThirdPartyCredential()) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> r
        }
    }

    /**
     * The DDG recovery code stored on the account is v1 shape (`{recovery:{primary_key, user_id}}`).
     * The v2 pairing wire requires v2.0 shape (`{recovery:{user_id, secret, cid, v}}`) per the
     * Recovery Payload Shape spec. Convert here before handing off to the runner.
     */
    override fun getDdgRecoveryCode(): Result<String> =
        when (val r = syncAccountRepository.getRecoveryCode()) {
            is Result.Success -> runCatching { toV2RecoveryCode(r.data.rawCode, cid = "ddg") }
                .fold(
                    onSuccess = { Result.Success(it) },
                    onFailure = { Result.Error(reason = "Failed to convert ddg recovery code to v2.0: ${it.message}") },
                )
            is Result.Error -> r
        }

    /** [ThirdPartyCredentialManager.getRecoveryCode] already emits v2.0 shape — no conversion. */
    override fun getThirdPartyRecoveryCode(): Result<String> =
        when (val r = syncAccountRepository.getThirdPartyRecoveryCode()) {
            is Result.Success -> Result.Success(r.data.rawCode)
            is Result.Error -> r
        }

    private fun toV2RecoveryCode(v1Base64: String, cid: String): String {
        val decoded = decodePermissiveBase64(v1Base64)
        val v1 = JSONObject(String(decoded, Charsets.UTF_8)).getJSONObject("recovery")
        val userId = v1.getString("user_id")
        val secret = v1.getString("primary_key")
        val v2Json = JSONObject().apply {
            put(
                "recovery",
                JSONObject().apply {
                    put("user_id", userId)
                    put("secret", secret)
                    put("cid", cid)
                    put("v", "2.0")
                },
            )
        }.toString()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(v2Json.toByteArray(Charsets.UTF_8))
    }

    /** Accept either standard or URL-safe base64 (with or without padding). */
    private fun decodePermissiveBase64(s: String): ByteArray {
        return runCatching { Base64.getUrlDecoder().decode(s) }
            .recoverCatching { Base64.getDecoder().decode(s) }
            .getOrThrow()
    }
}
