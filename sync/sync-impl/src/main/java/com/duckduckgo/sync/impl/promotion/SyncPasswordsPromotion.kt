/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.promotion

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Callback
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.api.promotion.SyncPromotions
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncPromoBinding
import com.duckduckgo.sync.impl.promotion.SyncPasswordsPromotionViewModel.Command
import com.duckduckgo.sync.impl.promotion.SyncPasswordsPromotionViewModel.Command.LaunchSyncSettings
import com.duckduckgo.sync.impl.promotion.SyncPasswordsPromotionViewModel.Command.ReevalutePromo
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@ContributesMultibinding(scope = AppScope::class)
class SyncPasswordsPromotion @Inject constructor(
    private val syncPromotions: SyncPromotions,
) : PasswordsScreenPromotionPlugin {

    override suspend fun getView(context: Context, numberSavedPasswords: Int): View? {
        return if (syncPromotions.canShowPasswordsPromotion(numberSavedPasswords)) {
            SyncPasswordsPromotionView(context)
        } else {
            null
        }
    }
}

@InjectWith(ViewScope::class)
class SyncPasswordsPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewSyncPromoBinding by viewBinding()

    private val viewModel: SyncPasswordsPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncPasswordsPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()
    private lateinit var coroutineScope: CoroutineScope

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        coroutineScope = CoroutineScope(SupervisorJob() + dispatchers.main())
        super.onAttachedToWindow()

        job += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(coroutineScope)

        configureMessage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
        job.cancel()
    }

    private fun processCommand(command: Command) {
        when (command) {
            LaunchSyncSettings -> launchSyncSettings()
            ReevalutePromo -> notifyPromoDismissed()
        }
    }

    private fun notifyPromoDismissed() {
        (context as? Callback)?.onPromoDismissed()
    }

    private fun launchSyncSettings() {
        context?.let {
            val intent = globalActivityStarter.startIntent(it, SyncActivityWithEmptyParams)
            context.startActivity(intent)
        }
    }

    private fun configureMessage() {
        with(binding.syncPromotion) {
            setMessage(
                Message(
                    topIllustration = CommonR.drawable.ic_sync_ok_48,
                    title = context.getString(R.string.autofillManagementSyncPromoTitle),
                    subtitle = context.getString(R.string.autofillManagementSyncPromoSubtitle),
                    action = context.getString(R.string.autofillManagementSyncPromoPrimaryButton),
                    action2 = context.getString(R.string.autofillManagementSyncPromoSecondaryButton),
                ),
            )
            onPrimaryActionClicked {
                viewModel.onUserSelectedSetUpSyncFromPromo()
            }
            onCloseButtonClicked {
                viewModel.onUserCancelledSyncPromo()
            }
            onSecondaryActionClicked {
                viewModel.onUserCancelledSyncPromo()
            }
            show()
        }
    }
}
