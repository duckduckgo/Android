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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class MintBackupRefreshTokenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var subscriptionsManager: SubscriptionsManager

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Mint backup refresh token now")
        binding.root.setSecondaryText("Deletes the stored backup and forces a token refresh, which mints a new one when backupRefreshToken is on")

        binding.root.setClickListener {
            findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch {
                authRepository.deleteBackupRefreshToken()
                val toastMessage = try {
                    subscriptionsManager.refreshAccessToken(force = true)
                    "Backup deleted and tokens refreshed"
                } catch (e: Exception) {
                    "Backup deleted, token refresh failed with ${e.javaClass.simpleName}"
                }
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@ContributesIntoSet(ActivityScope::class)
class MintBackupRefreshTokenViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return MintBackupRefreshTokenView(context)
    }
}
