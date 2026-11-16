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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.auth.BackupTokenManager
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ShowBackupRefreshTokenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var backupTokenManager: BackupTokenManager

    @Inject
    lateinit var timeProvider: CurrentTimeProvider

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Show backup refresh token")
        binding.root.setSecondaryText("Shows if a backup refresh token is stored, its age and if it belongs to the signed-in account")

        binding.root.setClickListener {
            findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch {
                val account = authRepository.getAccount()
                val backup = authRepository.getBackupRefreshToken()
                val toastMessage = if (backup == null) {
                    "No backup refresh token stored"
                } else {
                    val age = Duration.between(backup.issuedAt, Instant.ofEpochMilli(timeProvider.currentTimeMillis()))
                    val sameAccount = backup.accountExternalId == account?.externalId
                    val state = backupTokenManager.getState(accountExternalId = account?.externalId)?.paramValue
                    "Backup stored. Age: ${age.toDays()}d ${age.toHours() % 24}h ${age.toMinutes() % 60}m. Same account: $sameAccount. State: $state"
                }
                Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@ContributesIntoSet(ActivityScope::class)
class ShowBackupRefreshTokenViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return ShowBackupRefreshTokenView(context)
    }
}
