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

package com.duckduckgo.subscriptions.internal.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R
import com.duckduckgo.subscriptions.impl.auth.AuthClient
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

enum class StoredRefreshToken(val label: String) {
    MAIN(label = "main") {
        override suspend fun readJwt(authRepository: AuthRepository): String? = authRepository.getRefreshTokenV2()?.jwt
    },
    BACKUP(label = "backup") {
        override suspend fun readJwt(authRepository: AuthRepository): String? = authRepository.getBackupRefreshToken()?.jwt
    },
    ;

    abstract suspend fun readJwt(authRepository: AuthRepository): String?
}

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class InvalidateRefreshTokenView(
    context: Context,
    private val storedToken: StoredRefreshToken,
) : FrameLayout(context) {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var authClient: AuthClient

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Invalidate ${storedToken.label} refresh token")
        binding.root.setSecondaryText("Staging only. Marks the stored token as already used, so the next refresh fails")

        binding.root.setClickListener { showConfirmation() }
    }

    private fun showConfirmation() {
        TextAlertDialogBuilder(context)
            .setTitle("Invalidate the ${storedToken.label} refresh token?")
            .setMessage("The backend marks the stored ${storedToken.label} refresh token as already used. Nothing on this device changes.")
            .setPositiveButton("Invalidate", DESTRUCTIVE)
            .setNegativeButton(R.string.cancel)
            .setCancellable(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        invalidateAndShowResult()
                    }
                },
            )
            .show()
    }

    private fun invalidateAndShowResult() {
        findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch {
            val toastMessage = invalidateStoredToken()
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun invalidateStoredToken(): String {
        val label = storedToken.label.replaceFirstChar { it.uppercase() }
        return try {
            val jwt = storedToken.readJwt(authRepository = authRepository) ?: return "No ${storedToken.label} refresh token stored"
            authClient.invalidateRefreshToken(refreshToken = jwt)
            "$label refresh token invalidated. The next refresh with it will fail"
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == HTTP_NOT_FOUND) {
                "Not available on this backend (HTTP 404). A staging build is needed"
            } else {
                "Backend rejected the request with HTTP ${e.code()}. $label refresh token not invalidated"
            }
        } catch (e: IOException) {
            "Network error. $label refresh token not invalidated"
        } catch (e: Exception) {
            "$label refresh token not invalidated, failed with ${e.javaClass.simpleName}"
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

@ContributesIntoSet(ActivityScope::class)
class InvalidateMainRefreshTokenViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View = InvalidateRefreshTokenView(context = context, storedToken = StoredRefreshToken.MAIN)
}

@ContributesIntoSet(ActivityScope::class)
class InvalidateBackupRefreshTokenViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View = InvalidateRefreshTokenView(context = context, storedToken = StoredRefreshToken.BACKUP)
}
