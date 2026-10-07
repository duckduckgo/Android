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

package com.duckduckgo.sync.impl.auth

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.auth.AuthPrompt.Enroll
import com.duckduckgo.sync.impl.auth.AuthPrompt.Verify
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Renders prompts published by [DeviceAuthenticator2.currentPrompt] in the current activity.
 */
interface AuthPromptRenderer {
    /**
     * Call once per activity, from `onCreate`. When the activity is destroyed the shown prompt is dismissed without an answer.
     */
    fun bind(prompts: Flow<AuthPrompt?>)
}

@ContributesBinding(ActivityScope::class, boundType = AuthPromptRenderer::class)
class RealAuthPromptRenderer @Inject constructor(
    private val activity: AppCompatActivity,
    private val launcher: AuthLauncher,
    private val buildConfig: SyncBuildConfig,
) : AuthPromptRenderer {
    private var activePrompt: ShownPrompt? = null

    override fun bind(prompts: Flow<AuthPrompt?>) {
        fun dismissShown() {
            val forceDismiss = activePrompt?.forceDismiss
            activePrompt = null
            forceDismiss?.invoke()
        }

        activity.lifecycleScope.launch {
            try {
                activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    prompts.collect { prompt ->
                        if (prompt == activePrompt?.prompt) return@collect
                        dismissShown()
                        activePrompt = when (prompt) {
                            is Verify -> showBiometricPrompt(prompt)
                            is Enroll -> showEnrollmentDialog(prompt)
                            null -> null
                        }
                    }
                }
            } finally {
                dismissShown()
            }
        }
    }

    private fun showBiometricPrompt(prompt: Verify): ShownPrompt {
        val session = ViewModelProvider(activity, BiometricSessionViewModel)[BiometricSessionViewModel::class.java]
        val onResult: (AuthResult) -> Unit = { result ->
            session.runningPrompt = null
            prompt.answer {
                when (result) {
                    is AuthResult.Success -> onVerified()
                    is AuthResult.UserCancelled -> onCancelled()
                    is AuthResult.Error -> onError(result.reason)
                }
            }
        }

        // androidx keeps the system prompt alive across recreation and only needs a new callback. Cancelling it or
        // authenticating again would end the running session, and its ERROR_CANCELED would reach our new callback.
        val biometricPrompt = if (session.runningPrompt == prompt) {
            launcher.attach(activity, onResult)
        } else {
            session.runningPrompt = prompt
            launcher.launch(
                featureTitleText = prompt.title,
                featureAuthText = prompt.message,
                fragmentActivity = activity,
                onResult = onResult,
            )
        }
        prompt.onShown()
        return ShownPrompt(
            prompt = prompt,
            forceDismiss = { if (!activity.isChangingConfigurations) biometricPrompt.cancelAuthentication() },
        )
    }

    private fun showEnrollmentDialog(prompt: Enroll): ShownPrompt {
        val dialog = TextAlertDialogBuilder(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(prompt.cta)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onDialogShown() {
                        prompt.onShown()
                    }

                    override fun onPositiveButtonClicked() {
                        launchAuthEnrollment()
                    }

                    override fun onDialogDismissed() {
                        prompt.answer { onClosed() }
                    }
                },
            )
            .setCancellable(true)
        dialog.show()
        return ShownPrompt(
            prompt = prompt,
            forceDismiss = dialog::dismiss,
        )
    }

    // Dismissing a widget ourselves fires its callbacks too; we only forward answers for the prompt that is still shown.
    private fun <P : AuthPrompt> P.answer(block: P.() -> Unit) {
        if (activePrompt?.prompt == this) {
            activePrompt = null
            block()
        }
    }

    private class ShownPrompt(
        val prompt: AuthPrompt,
        val forceDismiss: () -> Unit,
    )

    private fun launchAuthEnrollment() {
        when {
            buildConfig.manufacturer.equals("Xiaomi", ignoreCase = true) -> {
                // Issue on Xiaomi: https://stackoverflow.com/questions/68484485/intent-action-fingerprint-enroll-on-redmi-results-in-exception
                Settings.ACTION_SETTINGS.safeLaunchSettingsActivity(tryFallback = false)
            }

            buildConfig.sdkInt >= 30 -> {
                @SuppressLint("InlinedApi")
                Settings.ACTION_BIOMETRIC_ENROLL.safeLaunchSettingsActivity(tryFallback = true)
            }

            else -> {
                @Suppress("DEPRECATION")
                Settings.ACTION_FINGERPRINT_ENROLL.safeLaunchSettingsActivity(tryFallback = true)
            }
        }
    }

    private fun String.safeLaunchSettingsActivity(tryFallback: Boolean) {
        try {
            activity.startActivity(Intent(this))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "${e.asLog()}. Trying fallback? $tryFallback" }
            if (tryFallback) {
                Settings.ACTION_SETTINGS.safeLaunchSettingsActivity(tryFallback = false)
            }
        }
    }
}

// androidx.biometric has no public way to check whether a prompt is showing. We could look up its fragment by tag, but the
// tag is an implementation detail, so we track the running prompt ourselves.
private class BiometricSessionViewModel : ViewModel() {
    var runningPrompt: Verify? = null

    companion object Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BiometricSessionViewModel() as T
    }
}
